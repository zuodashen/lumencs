package com.lumencs.agent;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class AgentState {
    private String sessionId;
    private String userLabel;
    private String userMessage;
    private String intent;
    /** 意图置信度 0~1，低于阈值触发澄清 */
    private double intentConfidence = 1.0;
    /** 低置信度：需要向用户澄清，不再派发业务 Agent */
    private boolean needsClarification;
    /** 澄清文案 */
    private String clarification;
    /** 三层记忆注入 Prompt 的上下文摘要 */
    private String memoryContext;
    private String currentAgent;
    private String finalResponse;
    private boolean compliancePassed = true;
    private List<String> violations = new ArrayList<>();
    private Map<String, Object> subResults = new HashMap<>();
    private List<Map<String, Object>> citations = new ArrayList<>();
    private String ticketNo;
    /** 合规未通过进入 HITL 收件箱的审核单号 */
    private Long reviewId;
    private boolean reviewPending;
    private boolean waitingCard;
    private boolean cardSubmit;
}
