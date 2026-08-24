package com.lumencs.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_tool_log")
public class ToolLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private String tool;
    private String argumentsJson;
    private String resultJson;
    private Boolean success;
    private Long durationMs;
    private LocalDateTime createdAt;
}
