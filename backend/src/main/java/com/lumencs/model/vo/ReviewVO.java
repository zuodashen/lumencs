package com.lumencs.model.vo;

import com.lumencs.model.entity.Review;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HITL 审核单出参 VO：violations 直接解析为列表，前端无需再解析 JSON 字符串。
 */
@Data
public class ReviewVO {
    private Long id;
    private String sessionId;
    private String originalContent;
    private String intent;
    private List<String> violations;
    private String status;
    private String reviewNote;
    private String reviewedBy;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    public static ReviewVO from(Review review, ObjectMapper objectMapper) {
        ReviewVO vo = new ReviewVO();
        vo.setId(review.getId());
        vo.setSessionId(review.getSessionId());
        vo.setOriginalContent(review.getOriginalContent());
        vo.setIntent(review.getIntent());
        vo.setStatus(review.getStatus());
        vo.setReviewNote(review.getReviewNote());
        vo.setReviewedBy(review.getReviewedBy());
        vo.setCreatedAt(review.getCreatedAt());
        vo.setReviewedAt(review.getReviewedAt());
        try {
            if (review.getViolationsJson() != null && !review.getViolationsJson().isBlank()) {
                vo.setViolations(objectMapper.readValue(review.getViolationsJson(), new TypeReference<>() {}));
            }
        } catch (Exception ignored) {
            vo.setViolations(List.of());
        }
        return vo;
    }
}
