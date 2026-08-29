package com.lumencs.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecallTestRequest {
    @NotBlank
    private String query;
    private Long documentId;
}
