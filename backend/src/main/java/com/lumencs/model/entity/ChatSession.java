package com.lumencs.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumencs.common.SuperEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_session")
public class ChatSession extends SuperEntity {
    @TableId
    private String id;
    private String userLabel;
}
