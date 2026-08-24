package com.lumencs.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumencs.common.SuperEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_ticket")
public class Ticket extends SuperEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ticketNo;
    private String sessionId;
    private String userLabel;
    private String title;
    private String description;
    private String status;
    private String priority;
}
