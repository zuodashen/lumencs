package com.lumencs.model.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CardRequest {
    private String sessionId;
    private String userLabel;
    private String cardId;
    private Map<String, Object> values;
}
