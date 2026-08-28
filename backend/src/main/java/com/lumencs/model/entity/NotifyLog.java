package com.lumencs.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_notify_log")
public class NotifyLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long channelId;
    private String eventType;
    private String eventId;
    private Boolean success;
    private String detail;
    private LocalDateTime createdAt;
}
