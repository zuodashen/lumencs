package com.lumencs.compliance;

import com.lumencs.agent.AgentEventSink;
import com.lumencs.agent.AgentState;
import com.lumencs.review.ReviewService;
import com.lumencs.tracing.AgentTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 合规审查两阶段：
 * 1. 规则引擎（毫秒级）：违禁金融用语 + PII 正则，命中直接拦截（不走 LLM，省成本）；
 * 2. LLM 深审：规则通过后对回复做语义级审查（越权承诺、误导、歧视等），
 *    不通过则进入 HITL 收件箱（cs_review），不直接回复用户。
 */
@Component
public class ComplianceCheckerAgent {

    private static final Logger log = LoggerFactory.getLogger(ComplianceCheckerAgent.class);

    private static final List<String> FORBIDDEN_TERMS = List.of(
            "保证收益", "稳赚不赔", "零风险", "保本保息",
            "最高收益", "预期收益率", "承诺回报",
            "内部消息", "内幕", "暗箱操作"
    );

    private static final Map<String, Pattern> PII_PATTERNS = Map.of(
            "phone", Pattern.compile("1[3-9]\\d{9}"),
            "id_card", Pattern.compile("\\d{17}[\\dXx]"),
            "bank_card", Pattern.compile("\\d{16,19}")
    );

    private static final String LLM_PROMPT = """
            你是金融客服回复合规审查员。判断待发送给用户的回复文本是否存在以下问题：
            - 承诺或暗示收益、保本、零风险等误导性表述
            - 越权承诺（如承诺人工无法保证的结果）
            - 歧视性、侮辱性内容
            - 身份冒充、诱导转账、索要密码/验证码
            - 泄露其他用户隐私
            只输出 JSON：{"passed": true/false, "violations": ["违规项..."], "reason": "一句话说明"}
            普通客服回复（如产品介绍、工单创建确认、奶茶下单确认）视为通过，violations 返回空数组。
            """;

    private final AgentTracer tracer;
    private final ChatClient chatClient;
    private final ReviewService reviewService;

    public ComplianceCheckerAgent(AgentTracer tracer, ChatClient chatClient, ReviewService reviewService) {
        this.tracer = tracer;
        this.chatClient = chatClient;
        this.reviewService = reviewService;
    }

    public AgentState process(AgentState state, AgentEventSink sink) {
        sink.step("compliance", "start", Map.of());
        StringBuilder content = new StringBuilder();
        state.getSubResults().forEach((key, value) -> {
            if (value instanceof String text) {
                content.append(text).append('\n');
            }
        });
        String text = content.toString();

        // 阶段一：规则快筛（critical 命中直接拦截，不调用 LLM）
        List<String> ruleViolations = tracer.trace(state.getSessionId(), "compliance", "rule_check", Map.of(),
                () -> ruleCheck(text));
        if (!ruleViolations.isEmpty()) {
            state.setCompliancePassed(false);
            state.setViolations(ruleViolations);
            sink.step("compliance", "done", Map.of(
                    "passed", false, "stage", "rule",
                    "violations", ruleViolations
            ));
            return state;
        }

        // 阶段二：LLM 深审；失败按通过处理（不因审查服务抖动误伤正常回复）
        ComplianceReview review;
        try {
            review = tracer.trace(state.getSessionId(), "compliance", "llm_review", Map.of(),
                    () -> llmReview(text));
        } catch (Exception e) {
            log.warn("llm compliance review failed, default pass: {}", e.getMessage());
            review = new ComplianceReview(true, List.of(), "");
        }

        if (review == null || review.passed()) {
            state.setCompliancePassed(true);
            sink.step("compliance", "done", Map.of("passed", true, "stage", "llm"));
            return state;
        }

        // LLM 判定不通过 → 进入 HITL 收件箱，不直接回复
        List<String> llmViolations = review.violations() == null ? List.of() : review.violations();
        Long reviewId = reviewService.enqueue(state.getSessionId(), text, state.getIntent(), llmViolations);
        state.setCompliancePassed(false);
        state.setViolations(llmViolations);
        state.setReviewPending(true);
        state.setReviewId(reviewId);
        sink.step("compliance", "done", Map.of(
                "passed", false, "stage", "llm", "hitl", true,
                "reviewId", reviewId, "violations", llmViolations
        ));
        return state;
    }

    private ComplianceReview llmReview(String content) {
        ComplianceReview review = chatClient.prompt()
                .system(LLM_PROMPT)
                .user(content)
                .call()
                .entity(ComplianceReview.class);
        return review == null ? new ComplianceReview(true, List.of(), "") : review;
    }

    private List<String> ruleCheck(String content) {
        List<String> violations = new ArrayList<>();
        for (String term : FORBIDDEN_TERMS) {
            if (content.contains(term)) {
                violations.add("包含违规金融用语: " + term);
            }
        }
        for (Map.Entry<String, Pattern> entry : PII_PATTERNS.entrySet()) {
            Matcher matcher = entry.getValue().matcher(content);
            if (matcher.find()) {
                String label = switch (entry.getKey()) {
                    case "phone" -> "手机号";
                    case "id_card" -> "身份证号";
                    case "bank_card" -> "银行卡号";
                    default -> entry.getKey();
                };
                violations.add("检测到 PII: " + label);
            }
        }
        return violations;
    }

    /** LLM 结构化输出：是否通过 + 违规项 + 原因 */
    public record ComplianceReview(boolean passed, List<String> violations, String reason) {
    }
}
