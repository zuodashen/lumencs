package com.lumencs.agent;

import com.lumencs.compliance.ComplianceCheckerAgent;
import com.lumencs.memory.LongTermMemoryService;
import com.lumencs.memory.WorkingMemoryService;
import com.lumencs.modules.blogwrite.BlogWriteGuard;
import com.lumencs.modules.workflow.WorkflowAgent;
import com.lumencs.modules.workflow.WorkflowCatalog;
import com.lumencs.tracing.AgentTracer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Supervisor 编排：意图路由（置信度低于阈值先澄清）→ 办事流程 / 知识 RAG
 * → 规则+LLM 合规（不通过进 HITL）→ 汇总。
 * 三层记忆（工作/短期/长期）在此汇入 AgentState，注入各 Agent 的 Prompt。
 */
@Component
public class SupervisorAgent {

    private final IntentRouterAgent intentRouter;
    private final KnowledgeRAGAgent knowledgeAgent;
    private final ChitchatAgent chitchatAgent;
    private final WorkflowAgent workflowAgent;
    private final ComplianceCheckerAgent complianceAgent;
    private final WorkingMemoryService workingMemory;
    private final LongTermMemoryService longTermMemory;
    private final AgentTracer tracer;

    public SupervisorAgent(
            IntentRouterAgent intentRouter,
            KnowledgeRAGAgent knowledgeAgent,
            ChitchatAgent chitchatAgent,
            WorkflowAgent workflowAgent,
            ComplianceCheckerAgent complianceAgent,
            WorkingMemoryService workingMemory,
            LongTermMemoryService longTermMemory,
            AgentTracer tracer) {
        this.intentRouter = intentRouter;
        this.knowledgeAgent = knowledgeAgent;
        this.chitchatAgent = chitchatAgent;
        this.workflowAgent = workflowAgent;
        this.complianceAgent = complianceAgent;
        this.workingMemory = workingMemory;
        this.longTermMemory = longTermMemory;
        this.tracer = tracer;
    }

    public AgentState orchestrate(AgentState state, AgentEventSink sink) {
        return tracer.trace(state.getSessionId(), "supervisor", "orchestrate", Map.of(), () -> {
            sink.step("supervisor", "start", Map.of("cardSubmit", state.isCardSubmit()));
            if (state.isCardSubmit()) {
                String intent = workingMemory.getString(state.getSessionId(), "intent");
                state.setIntent(intent.isBlank() ? "refund" : intent);
                state.setIntentConfidence(1.0);
            } else {
                intentRouter.process(state, sink);
                workingMemory.put(state.getSessionId(), "intent", state.getIntent());
            }

            if (state.isNeedsClarification()) {
                // 低置信度：不派发业务 Agent，要求用户澄清
                state.getSubResults().put("clarification", state.getClarification());
                sink.step("supervisor", "clarify", Map.of(
                        "intent", state.getIntent() == null ? "" : state.getIntent(),
                        "confidence", state.getIntentConfidence()
                ));
            } else {
                fillMemoryContext(state);
                dispatch(state, sink);
                if ("compliance_checker".equals(state.getIntent()) && state.getSubResults().isEmpty()) {
                    state.getSubResults().put("compliance_notice",
                            "已记录您的安全相关诉求。建议通过官方渠道核实，并等待人工客服介入。");
                }
                if (!state.isWaitingCard() && !state.isReviewPending()
                        && (state.getIntent() == null || !state.getIntent().startsWith("blog_"))) {
                    complianceAgent.process(state, sink);
                }
            }
            synthesize(state);
            sink.step("supervisor", "done", Map.of(
                    "intent", state.getIntent() == null ? "" : state.getIntent(),
                    "confidence", state.getIntentConfidence(),
                    "waitingCard", state.isWaitingCard(),
                    "compliancePassed", state.isCompliancePassed(),
                    "reviewId", state.getReviewId() == null ? 0 : state.getReviewId()
            ));
            return state;
        });
    }

    private void dispatch(AgentState state, AgentEventSink sink) {
        String intent = state.getIntent();
        if (BlogWriteGuard.isWriteIntent(intent) && !state.isHubOperator()) {
            state.getSubResults().put("hub_auth", BlogWriteGuard.LOGIN_HINT);
            return;
        }
        if (WorkflowCatalog.isWorkflow(intent)) {
            workflowAgent.process(state, sink);
            return;
        }
        if ("compliance_checker".equals(intent)) {
            return;
        }
        if ("chitchat".equals(intent)) {
            chitchatAgent.process(state, sink);
            return;
        }
        knowledgeAgent.process(state, sink);
    }

    /**
     * 三层记忆进 Prompt：工作记忆快照（流程/槽位）+ 长期画像（口味/工位）
     * 拼成上下文注入 AgentState；短期记忆由各 Agent 自行读取。
     */
    private void fillMemoryContext(AgentState state) {
        Map<String, Object> working = workingMemory.snapshot(state.getSessionId());
        Map<String, Object> profile = longTermMemory.profile(state.getUserLabel());
        StringBuilder sb = new StringBuilder();
        sb.append("工作记忆: intent=").append(String.valueOf(working.getOrDefault("intent", "")))
                .append(", workflow=").append(String.valueOf(working.getOrDefault("workflow", "")))
                .append(", slots=").append(String.valueOf(working.getOrDefault("slots", "{}")));
        if (!profile.isEmpty()) {
            sb.append("; 用户画像: ").append(profile);
        }
        if (state.getArticleSlug() != null && !state.getArticleSlug().isBlank()) {
            sb.append("; 单文问答范围 slug=").append(state.getArticleSlug());
        }
        state.setMemoryContext(sb.toString());
    }

    private void synthesize(AgentState state) {
        if (state.isNeedsClarification()) {
            state.setFinalResponse(state.getClarification() == null ? "请重新描述一下您的问题。" : state.getClarification());
            return;
        }
        if (state.isReviewPending()) {
            state.setFinalResponse("您的咨询内容已转人工审核（审核单 #" + state.getReviewId()
                    + "），审核通过后会尽快回复您。");
            return;
        }
        if (!state.isCompliancePassed()) {
            state.setFinalResponse("抱歉，回复涉及敏感或不合规内容，已拦截并建议转人工处理。"
                    + (state.getViolations().isEmpty() ? "" : " 原因：" + String.join("；", state.getViolations())));
            return;
        }
        StringBuilder sb = new StringBuilder();
        state.getSubResults().forEach((key, value) -> {
            if (value instanceof String text && !text.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(text);
            }
        });
        state.setFinalResponse(sb.isEmpty() ? "暂时无法处理该请求，请稍后重试或转人工。" : sb.toString());
    }
}
