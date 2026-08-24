package com.lumencs.ticket;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 工单状态流转入参（走状态机校验）。
 */
@Data
public class TicketStatusRequest {
    @NotBlank
    private String status;
}
