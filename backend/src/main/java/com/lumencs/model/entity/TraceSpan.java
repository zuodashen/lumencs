package com.lumencs.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_span")
public class TraceSpan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private Long messageId;
    private String agent;
    private String method;
    private String status;
    private Long durationMs;
    private String detailJson;
    private LocalDateTime createdAt;
}
