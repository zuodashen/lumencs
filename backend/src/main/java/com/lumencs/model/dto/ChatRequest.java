package com.lumencs.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {
    private String sessionId;
    private String userLabel;
    @NotBlank
    private String message;
}
