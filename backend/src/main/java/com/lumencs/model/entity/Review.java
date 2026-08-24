package com.lumencs.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_review")
public class Review {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private Long messageId;
    private String originalContent;
    private String intent;
    private String violationsJson;
    private String status;
    private String reviewNote;
    private String reviewedBy;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
