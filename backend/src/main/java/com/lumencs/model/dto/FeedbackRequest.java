package com.lumencs.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FeedbackRequest {
    private String sessionId;
    @NotNull
    private Long messageId;
    @NotBlank
    private String score;
    private Boolean cited;
    private String comment;
}
