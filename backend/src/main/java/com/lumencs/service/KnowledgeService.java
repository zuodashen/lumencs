package com.lumencs.service;

import com.lumencs.model.entity.KbChunk;
import com.lumencs.mapper.KbChunkMapper;
import com.lumencs.model.entity.KbDocument;
import com.lumencs.mapper.KbDocumentMapper;
import com.lumencs.knowledge.TextChunker;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumencs.rag.RagClient;
import com.lumencs.rag.RagHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final RagClient ragClient;
    private final int topK;

    public KnowledgeService(
            KbDocumentMapper documentMapper,
            KbChunkMapper chunkMapper,
            RagClient ragClient,
            @Value("${lumencs.rag.top-k}") int topK) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.ragClient = ragClient;
        this.topK = topK;
    }

    public List<KbDocument> listDocuments() {
        return documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .orderByDesc(KbDocument::getId));
    }

    /** 控制台知识库列表：分页。 */
    public com.baomidou.mybatisplus.core.metadata.IPage<KbDocument> listDocumentsPage(long pageNum, long pageSize) {
        return documentMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<KbDocument>().orderByDesc(KbDocument::getId));
    }

    @Transactional
    public KbDocument ingest(String title, String source, String content) {
        KbDocument doc = new KbDocument();
        doc.setTitle(title);
        doc.setSource(source == null || source.isBlank() ? title : source);
        doc.setContent(content);
        doc.setStatus("INDEXING");
        doc.setChunkCount(0);
        documentMapper.insert(doc);

        List<String> parts = TextChunker.chunk(content, 512, 80);
        List<KbChunk> chunks = new ArrayList<>();
        List<Map<String, Object>> points = new ArrayList<>();
        int order = 0;
        for (String part : parts) {
            KbChunk chunk = new KbChunk();
            chunk.setId(UUID.randomUUID().toString());
            chunk.setDocumentId(doc.getId());
            chunk.setContent(part);
            chunk.setSource(doc.getSource());
            chunk.setSortOrder(order++);
            chunkMapper.insert(chunk);
            chunks.add(chunk);

            Map<String, Object> payload = new HashMap<>();
            payload.put("document_id", doc.getId());
            payload.put("content", part);
            payload.put("source", doc.getSource());
            payload.put("title", doc.getTitle());
            points.add(Map.of(
                    "id", chunk.getId(),
                    "text", part,
                    "payload", payload
            ));
        }

        doc.setChunkCount(chunks.size());
        try {
            ragClient.ingest(points);
            doc.setStatus("READY");
        } catch (Exception e) {
            log.warn("vector ingest failed, keyword fallback remains available", e);
            doc.setStatus("KEYWORD_ONLY");
        }
        documentMapper.updateById(doc);
        return doc;
    }

    public List<RagHit> search(String query) {
        try {
            List<RagHit> hits = ragClient.search(query, topK);
            if (!hits.isEmpty()) {
                return hits;
            }
        } catch (Exception e) {
            log.warn("vector search failed, fallback to keyword", e);
        }
        return keywordSearch(query);
    }

    private List<RagHit> keywordSearch(String query) {
        Set<String> terms = Set.of(query.toLowerCase().split("[\\s,，。！？?]+"));
        List<KbChunk> chunks = chunkMapper.selectList(null);
        return chunks.stream()
                .map(chunk -> {
                    String content = chunk.getContent().toLowerCase();
                    long score = terms.stream().filter(t -> !t.isBlank() && content.contains(t)).count();
                    RagHit hit = new RagHit();
                    hit.setId(chunk.getId());
                    hit.setScore(score);
                    hit.setContent(chunk.getContent());
                    hit.setSource(chunk.getSource());
                    hit.setDocumentId(chunk.getDocumentId());
                    return hit;
                })
                .filter(hit -> hit.getScore() > 0)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .toList();
    }
}
