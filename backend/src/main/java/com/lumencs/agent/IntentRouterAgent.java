package com.lumencs.agent;

import com.lumencs.memory.WorkingMemoryService;
import com.lumencs.modules.workflow.WorkflowCatalog;
import com.lumencs.tracing.AgentTracer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 意图路由：关键词优先（确定性，置信度高）；未命中再调 LLM，
 * LLM 返回 JSON {intent, confidence}。置信度低于阈值时标记需要澄清，
 * Supervisor 不再派发业务 Agent，改为向用户确认意图。
 */
@Component
public class IntentRouterAgent {

    private static final Set<String> INTENTS = Set.of(
            "knowledge_rag", "refund", "account_open", "ticket_query", "complaint", "compliance_checker", "milk_tea"
    );

    /** 低于该置信度触发澄清（LLM 兜底默认 0.6，仅 LLM 明确低置信才澄清） */
    private static final double CLARIFY_THRESHOLD = 0.55;
    /** 关键词确定性命中（规则而非模型判断）的置信度 */
    private static final double RULE_CONFIDENCE = 0.95;

    private static final String SYSTEM_PROMPT = """
            你是意图识别Agent。只返回 JSON，格式：{"intent": "...", "confidence": 0.0-1.0}
            intent 只能是以下之一：
            knowledge_rag, refund, account_open, ticket_query, complaint, compliance_checker, milk_tea
            规则：
            - 产品咨询、政策、利率、怎么办理的说明 → knowledge_rag
            - 我要退款、申请退钱 → refund
            - 我要开户、开通账户 → account_open
            - 查工单、工单进度 → ticket_query
            - 投诉、不满意 → complaint
            - 盗刷、欺诈、举报 → compliance_checker
            - 点奶茶、点咖啡、下午茶、口渴、加班喝一杯、再来一杯 → milk_tea
            confidence 表示你对意图判断的把握程度：用户表述明确时接近 1.0；
            意图模糊、无法确定、可能是闲聊时 confidence 应低于 0.5。
            """;

    private static final String CLARIFICATION_TEXT = """
            我还没太理解您的意思。您是想：
            1. 咨询产品 / 政策（如收益、利率、开户流程）
            2. 办理退款
            3. 开通账户
            4. 查询工单进度
            5. 投诉建议
            6. 举报欺诈 / 盗刷等安全问题
            7. 点一杯奶茶（工位奶茶局）
            请重新描述一下，我来帮您处理。""";

    private final ChatClient chatClient;
    private final AgentTracer tracer;
    private final WorkingMemoryService workingMemory;

    public IntentRouterAgent(ChatClient chatClient, AgentTracer tracer, WorkingMemoryService workingMemory) {
        this.chatClient = chatClient;
        this.tracer = tracer;
        this.workingMemory = workingMemory;
    }

    public AgentState process(AgentState state, AgentEventSink sink) {
        sink.step("intent_router", "start", Map.of());
        IntentResult result = tracer.trace(state.getSessionId(), "intent_router", "process", Map.of(),
                () -> classify(state));
        state.setIntent(result.intent());
        state.setIntentConfidence(result.confidence());
        if (result.confidence() < CLARIFY_THRESHOLD) {
            state.setNeedsClarification(true);
            state.setClarification(CLARIFICATION_TEXT);
        }
        sink.step("intent_router", "done", Map.of(
                "intent", result.intent(),
                "confidence", result.confidence(),
                "clarify", state.isNeedsClarification(),
                "workflow", WorkflowNames.workflow(result.intent())
        ));
        return state;
    }

    private IntentResult classify(AgentState state) {
        String message = state.getUserMessage() == null ? "" : state.getUserMessage();
        String last = workingMemory.getString(state.getSessionId(), "intent");
        String pending = workingMemory.getString(state.getSessionId(), "pendingCardId");
        String keyed = keywordFallback(message);

        // 办事流程续接：卡片未提交时短句不打断流程
        if (!pending.isBlank() && WorkflowCatalog.isWorkflow(last)) {
            if (isStrongSwitch(keyed, last, message)) {
                workingMemory.clearWorkflow(state.getSessionId());
            } else {
                return new IntentResult(last, RULE_CONFIDENCE);
            }
        }
        // 追问「再来一杯」延续上次流程
        if (isFollowUp(message) && WorkflowCatalog.isWorkflow(last)) {
            return new IntentResult(last, RULE_CONFIDENCE);
        }
        // 关键词确定性命中（非默认 knowledge_rag）
        if (!"knowledge_rag".equals(keyed)) {
            return new IntentResult(keyed, RULE_CONFIDENCE);
        }
        // 关键词未命中 → LLM 结构化输出（intent + confidence）
        try {
            IntentResult result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(message)
                    .call()
                    .entity(IntentResult.class);
            if (result != null && result.intent() != null && INTENTS.contains(result.intent().trim().toLowerCase())) {
                return new IntentResult(result.intent().trim().toLowerCase(), clamp(result.confidence()));
            }
        } catch (Exception ignored) {
            // 关键词已兜底
        }
        return new IntentResult(keyed, 0.6);
    }

    private boolean isStrongSwitch(String keyed, String last, String message) {
        if (!keyed.equals(last) && WorkflowCatalog.isWorkflow(keyed)) {
            return true;
        }
        if ("knowledge_rag".equals(keyed)
                && !WorkflowCatalog.mentionsSlotOption(last, message)
                && containsAny(message, "多少", "怎么", "什么", "政策", "收益", "利率")) {
            return true;
        }
        return false;
    }

    private boolean isFollowUp(String message) {
        return containsAny(message, "再来", "老样子", "同样", "再点", "还是那", "老工位", "一样的");
    }

    private String keywordFallback(String message) {
        String msg = message == null ? "" : message;
        if (containsAny(msg, "退款", "退钱", "退货")) {
            return "refund";
        }
        if (containsAny(msg, "开户", "开个户")) {
            return "account_open";
        }
        if (containsAny(msg, "工单号", "查工单", "工单进度")) {
            return "ticket_query";
        }
        if (containsAny(msg, "投诉", "不满意")) {
            return "complaint";
        }
        if (containsAny(msg, "奶茶", "咖啡", "点单", "下午茶", "口渴", "生椰", "伯牙", "再来一杯")) {
            return "milk_tea";
        }
        if (containsAny(msg, "举报", "欺诈", "盗刷", "泄露")) {
            return "compliance_checker";
        }
        return "knowledge_rag";
    }

    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** LLM 结构化输出：意图 + 置信度 */
    public record IntentResult(String intent, double confidence) {
    }

    private static final class WorkflowNames {
        static String workflow(String intent) {
            return Set.of("refund", "account_open", "ticket_query", "complaint", "milk_tea").contains(intent)
                    ? intent : "none";
        }
    }
}
