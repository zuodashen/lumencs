package com.lumencs.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDocumentRequest {
    @NotBlank
    private String title;
    private String source;
    @NotBlank
    private String content;
}
