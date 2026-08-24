package com.lumencs.common;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计字段基类：created_at / updated_at / create_user / update_user 由
 * {@link com.lumencs.config.MybatisMetaHandler} 自动填充（INSERT / INSERT_UPDATE）。
 */
@Data
public abstract class SuperEntity {

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "create_user", fill = FieldFill.INSERT)
    private String createUser;

    @TableField(value = "update_user", fill = FieldFill.INSERT_UPDATE)
    private String updateUser;
}
