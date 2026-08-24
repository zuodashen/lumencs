package com.lumencs.tracing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TraceSpanMapper extends BaseMapper<TraceSpan> {
}
