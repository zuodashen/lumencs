package com.lumencs.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumencs.common.SuperEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_document")
public class KbDocument extends SuperEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String source;
    private String content;
    private String status;
    private Integer chunkCount;
}
