package com.lumencs.model.dto;

import com.lumencs.common.PageDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审核单分页查询：pageNum / pageSize / status（可选过滤 PENDING / APPROVED / REJECTED）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewQueryDTO extends PageDto {
    private String status;
}
