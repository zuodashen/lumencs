package com.lumencs.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 统一分页返回：total / pageNum / pageSize / records。
 */
@Data
public class PageWrapper<T> {

    private long total;
    private long pageNum;
    private long pageSize;
    private List<T> records;

    public static <T> PageWrapper<T> of(IPage<T> page) {
        PageWrapper<T> wrapper = new PageWrapper<>();
        wrapper.setTotal(page.getTotal());
        wrapper.setPageNum(page.getCurrent());
        wrapper.setPageSize(page.getSize());
        wrapper.setRecords(page.getRecords());
        return wrapper;
    }

    public static <T> PageWrapper<T> of(long total, long pageNum, long pageSize, List<T> records) {
        PageWrapper<T> wrapper = new PageWrapper<>();
        wrapper.setTotal(total);
        wrapper.setPageNum(pageNum);
        wrapper.setPageSize(pageSize);
        wrapper.setRecords(records);
        return wrapper;
    }
}
