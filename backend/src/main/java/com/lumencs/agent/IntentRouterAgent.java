package com.lumencs.agent;

import com.lumencs.memory.WorkingMemoryService;
import com.lumencs.modules.skill.SkillRegistry;
import com.lumencs.modules.workflow.WorkflowCatalog;
import com.lumencs.tracing.AgentTracer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 意图路由：Skill 关键词优先（确定性）；未命中再调 LLM（只看 Skill 目录的 name/description）。
 */
@Component
public class IntentRouterAgent {

    private static final double CLARIFY_THRESHOLD = 0.55;
    private static final double RULE_CONFIDENCE = 0.95;

    private static final String CLARIFICATION_TEXT = """
            我还没太理解。您是想：
            1. 问知识库里的笔记 / 文档
            2. 记一笔到知识库
            3. 加一条待办
            4. 看待办列表 / 查进度
            5. 改待办状态
            6. 写博客 / 存草稿 / 看已发布列表 / 同步一篇
            7. 查一只股票行情
            8. 点一杯奶茶（演示）
            9. 随便聊聊
            请再说具体一点。""";

    private final ChatClient chatClient;
    private final AgentTracer tracer;
    private final WorkingMemoryService workingMemory;
    private final SkillRegistry skillRegistry;

    public IntentRouterAgent(
            ChatClient chatClient,
            AgentTracer tracer,
            WorkingMemoryService workingMemory,
            SkillRegistry skillRegistry) {
        this.chatClient = chatClient;
        this.tracer = tracer;
        this.workingMemory = workingMemory;
        this.skillRegistry = skillRegistry;
    }

    public AgentState process(AgentState state, AgentEventSink sink) {
        sink.step("intent_router", "start", Map.of("skills", skillRegistry.all().size()));
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
        String keyed = skillRegistry.matchIntent(message);

        if (isCancel(message) && (!pending.isBlank() || WorkflowCatalog.isWorkflow(last))) {
            workingMemory.clearWorkflow(state.getSessionId());
            if (isOnlyCancel(message)) {
                return new IntentResult("workflow_cancel", RULE_CONFIDENCE);
            }
            pending = "";
            last = "";
            keyed = skillRegistry.matchIntent(message);
        }

        if (!pending.isBlank() && WorkflowCatalog.isWorkflow(last)) {
            if (isStrongSwitch(keyed, last, message)) {
                workingMemory.clearWorkflow(state.getSessionId());
            } else {
                return new IntentResult(last, RULE_CONFIDENCE);
            }
        }
        if (isFollowUp(message) && WorkflowCatalog.isWorkflow(last)) {
            return new IntentResult(last, RULE_CONFIDENCE);
        }
        if (!"knowledge_rag".equals(keyed)) {
            return new IntentResult(keyed, RULE_CONFIDENCE);
        }
        try {
            IntentResult result = chatClient.prompt()
                    .system(skillRegistry.intentCatalogPrompt())
                    .user(message)
                    .call()
                    .entity(IntentResult.class);
            Set<String> allowed = skillRegistry.intents();
            if (result != null && result.intent() != null && allowed.contains(result.intent().trim().toLowerCase())) {
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
                && containsAny(message, List.of("多少", "怎么", "什么", "文档", "笔记", "知识库"))) {
            return true;
        }
        if ("stock_quote".equals(keyed) && !"stock_quote".equals(last)) {
            return true;
        }
        return false;
    }

    private boolean isCancel(String message) {
        if (containsAny(message, skillRegistry.cancelExclude())) {
            return false;
        }
        return containsAny(message, skillRegistry.cancelPhrases());
    }

    private boolean isOnlyCancel(String message) {
        String rest = message == null ? "" : message;
        List<String> phrases = skillRegistry.cancelPhrases().stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();
        for (String phrase : phrases) {
            rest = rest.replace(phrase, "");
        }
        rest = rest.replaceAll("[，,。！？?\\s]+", "").trim();
        return rest.length() <= 2;
    }

    private boolean isFollowUp(String message) {
        return containsAny(message, skillRegistry.followUpPhrases());
    }

    private boolean containsAny(String text, List<String> keys) {
        if (text == null || keys == null || keys.isEmpty()) {
            return false;
        }
        for (String key : keys) {
            if (key != null && !key.isBlank() && text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record IntentResult(String intent, double confidence) {
    }

    private static final class WorkflowNames {
        static String workflow(String intent) {
            return WorkflowCatalog.isWorkflow(intent) ? intent : "none";
        }
    }
}
