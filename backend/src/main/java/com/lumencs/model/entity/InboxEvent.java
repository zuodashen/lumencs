package com.lumencs.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_inbox")
public class InboxEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventType;
    private String eventId;
    private String title;
    private String body;
    private Boolean readFlag;
    private LocalDateTime createdAt;
}
