package com.lumencs.review;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 审核决定入参：APPROVE（通过） / REJECT（驳回）。
 */
@Data
public class ReviewDecideRequest {
    @NotBlank
    private String action;
    private String note;
}
