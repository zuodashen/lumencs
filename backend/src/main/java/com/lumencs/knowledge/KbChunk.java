package com.lumencs.knowledge;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("cs_chunk")
public class KbChunk {
    @TableId
    private String id;
    private Long documentId;
    private String content;
    private String source;
    private Integer sortOrder;
}
