package com.lumencs.model.dto;

import lombok.Data;

@Data
public class FaqDraftRequest {
    private String sessionId;
    private Long messageId;
}
