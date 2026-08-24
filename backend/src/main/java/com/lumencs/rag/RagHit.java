package com.lumencs.rag;

import lombok.Data;

@Data
public class RagHit {
    private String id;
    private double score;
    private String content;
    private String source;
    private Long documentId;
}
