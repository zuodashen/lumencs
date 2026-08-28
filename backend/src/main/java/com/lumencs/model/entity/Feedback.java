package com.lumencs.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_feedback")
public class Feedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private Long messageId;
    private String score;
    private Boolean cited;
    private String comment;
    private LocalDateTime createdAt;
}
