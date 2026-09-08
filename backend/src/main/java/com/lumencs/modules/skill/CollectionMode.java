package com.lumencs.modules.skill;

/**
 * 办事 Skill 怎么收集参数。
 * <ul>
 *   <li>{@link #CONVERSATION}：缺槽时用对话追问，齐了再出确认卡</li>
 *   <li>{@link #FORM}：直接出确认卡（写博客等）</li>
 *   <li>{@link #DIRECT}：不弹卡，立刻调工具（列表 / 行情）</li>
 * </ul>
 */
public enum CollectionMode {
    FORM,
    CONVERSATION,
    DIRECT;

    public static CollectionMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase()) {
            case "conversation" -> CONVERSATION;
            case "direct" -> DIRECT;
            case "form" -> FORM;
            default -> FORM;
        };
    }

    public boolean isDirect() {
        return this == DIRECT;
    }

    public boolean isConversation() {
        return this == CONVERSATION;
    }
}
