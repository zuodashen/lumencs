package com.lumencs.model.vo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumencs.model.entity.ChatMessage;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 聊天消息出参 VO：citations 解析为列表，前端无需再解析 JSON 字符串。
 */
@Data
public class MessageVO {
    private Long id;
    private String sessionId;
    private String role;
    private String content;
    private String intent;
    private List<Map<String, Object>> citations;
    private Map<String, Object> embed;
    private Map<String, Object> card;
    private LocalDateTime createdAt;

    public static MessageVO from(ChatMessage message, ObjectMapper objectMapper) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setSessionId(message.getSessionId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setIntent(message.getIntent());
        vo.setCreatedAt(message.getCreatedAt());
        try {
            if (message.getCitationsJson() != null && !message.getCitationsJson().isBlank()) {
                vo.setCitations(objectMapper.readValue(message.getCitationsJson(), new TypeReference<>() {}));
            }
        } catch (Exception ignored) {
            vo.setCitations(List.of());
        }
        try {
            if (message.getEmbedJson() != null && !message.getEmbedJson().isBlank()) {
                vo.setEmbed(objectMapper.readValue(message.getEmbedJson(), new TypeReference<>() {}));
            }
        } catch (Exception ignored) {
            vo.setEmbed(null);
        }
        try {
            if (message.getCardJson() != null && !message.getCardJson().isBlank()) {
                vo.setCard(objectMapper.readValue(message.getCardJson(), new TypeReference<>() {}));
            }
        } catch (Exception ignored) {
            vo.setCard(null);
        }
        return vo;
    }
}
