package com.lumencs.model.dto;

import com.lumencs.common.PageDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单分页查询：pageNum / pageSize / status（可选过滤）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TicketQueryDTO extends PageDto {
    private String status;
}
