package com.lumencs.model.vo;

import com.lumencs.model.entity.TraceSpan;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 调用追踪出参 VO。
 */
@Data
public class TraceSpanVO {
    private Long id;
    private String sessionId;
    private Long messageId;
    private String agent;
    private String method;
    private String status;
    private Long durationMs;
    private String detailJson;
    private LocalDateTime createdAt;

    public static TraceSpanVO from(TraceSpan span) {
        TraceSpanVO vo = new TraceSpanVO();
        vo.setId(span.getId());
        vo.setSessionId(span.getSessionId());
        vo.setMessageId(span.getMessageId());
        vo.setAgent(span.getAgent());
        vo.setMethod(span.getMethod());
        vo.setStatus(span.getStatus());
        vo.setDurationMs(span.getDurationMs());
        vo.setDetailJson(span.getDetailJson());
        vo.setCreatedAt(span.getCreatedAt());
        return vo;
    }
}
