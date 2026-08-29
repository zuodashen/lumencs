package com.lumencs.model.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 工单状态机。
 * CREATED → PROCESSING → WAITING_HUMAN → RESOLVED → CLOSED；任意阶段可 ESCALATED。
 * 非法流转抛 {@link IllegalArgumentException}，由全局异常转 400。
 */
public enum TicketStatus {

    CREATED, PROCESSING, WAITING_HUMAN, RESOLVED, CLOSED, ESCALATED;

    private static final Map<TicketStatus, Set<TicketStatus>> TRANSITIONS = Map.of(
            CREATED, EnumSet.of(PROCESSING, ESCALATED, CLOSED),
            PROCESSING, EnumSet.of(WAITING_HUMAN, RESOLVED, ESCALATED),
            WAITING_HUMAN, EnumSet.of(PROCESSING, RESOLVED, ESCALATED),
            RESOLVED, EnumSet.of(CLOSED, PROCESSING, ESCALATED),
            ESCALATED, EnumSet.of(PROCESSING, WAITING_HUMAN, RESOLVED),
            CLOSED, EnumSet.noneOf(TicketStatus.class)
    );

    public boolean canTransitionTo(TicketStatus target) {
        return TRANSITIONS.getOrDefault(this, EnumSet.noneOf(TicketStatus.class)).contains(target);
    }

    public String zh() {
        return switch (this) {
            case CREATED -> "已创建";
            case PROCESSING -> "进行中";
            case WAITING_HUMAN -> "等待处理";
            case RESOLVED -> "已完成";
            case CLOSED -> "已关闭";
            case ESCALATED -> "已升级";
        };
    }

    public static String zhOf(String raw) {
        try {
            return parse(raw).zh();
        } catch (IllegalArgumentException e) {
            return raw == null ? "" : raw;
        }
    }

    public static TicketStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("工单状态不能为空");
        }
        try {
            return TicketStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知工单状态: " + raw);
        }
    }
}
