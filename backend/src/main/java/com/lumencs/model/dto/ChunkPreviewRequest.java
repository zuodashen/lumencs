package com.lumencs.model.dto;

import lombok.Data;

@Data
public class ChunkPreviewRequest {
    private String content;
    private Boolean collapseWhitespace;
    private Boolean paragraphSplit;
    private Integer parentMax;
    private Integer childMax;
}
