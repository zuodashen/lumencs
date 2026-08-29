package com.lumencs.model.vo;

import com.lumencs.model.entity.KbDocument;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文档出参 VO（列表不含大段 content，控制载荷）。
 */
@Data
public class DocumentVO {
    private Long id;
    private String title;
    private String source;
    private String status;
    private Integer chunkCount;
    private Integer charCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DocumentVO from(KbDocument doc) {
        DocumentVO vo = new DocumentVO();
        vo.setId(doc.getId());
        vo.setTitle(doc.getTitle());
        vo.setSource(doc.getSource());
        vo.setStatus(doc.getStatus());
        vo.setChunkCount(doc.getChunkCount());
        vo.setCharCount(doc.getContent() == null ? 0 : doc.getContent().length());
        vo.setCreatedAt(doc.getCreatedAt());
        vo.setUpdatedAt(doc.getUpdatedAt());
        return vo;
    }
}
