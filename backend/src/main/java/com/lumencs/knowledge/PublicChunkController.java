package com.lumencs.knowledge;

import com.lumencs.common.ApiResponse;
import com.lumencs.common.R;
import com.lumencs.exception.BizException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公开引用详情：聊天页点引用时按 chunkId 拉取完整原文（只读，无需登录）。
 */
@RestController
@RequestMapping("/api/knowledge")
public class PublicChunkController {

    private final KbChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;

    public PublicChunkController(KbChunkMapper chunkMapper, KbDocumentMapper documentMapper) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
    }

    @GetMapping("/chunks/{id}")
    public R<Map<String, Object>> chunk(@PathVariable String id) {
        KbChunk chunk = chunkMapper.selectById(id);
        if (chunk == null) {
            throw new BizException("引用内容不存在");
        }
        KbDocument doc = chunk.getDocumentId() == null ? null : documentMapper.selectById(chunk.getDocumentId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", chunk.getId());
        body.put("documentId", chunk.getDocumentId());
        body.put("source", chunk.getSource());
        body.put("title", doc == null ? chunk.getSource() : doc.getTitle());
        body.put("content", chunk.getContent());
        return R.success(body);
    }
}
