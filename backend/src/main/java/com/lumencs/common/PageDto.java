package com.lumencs.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询基类：GET query 直接绑定 pageNum / pageSize。
 */
@Data
public class PageDto {

    @Min(1)
    private long pageNum = 1;

    @Min(1)
    @Max(100)
    private long pageSize = 10;

    public long getOffset() {
        return (pageNum - 1) * pageSize;
    }
}
