package com.lumencs.model.vo;

import com.lumencs.model.entity.ChatSession;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话出参 VO。
 */
@Data
public class SessionVO {
    private String id;
    private String userLabel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SessionVO from(ChatSession session) {
        SessionVO vo = new SessionVO();
        vo.setId(session.getId());
        vo.setUserLabel(session.getUserLabel());
        vo.setCreatedAt(session.getCreatedAt());
        vo.setUpdatedAt(session.getUpdatedAt());
        return vo;
    }
}
