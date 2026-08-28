package com.lumencs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumencs.model.entity.InboxEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InboxEventMapper extends BaseMapper<InboxEvent> {
}
