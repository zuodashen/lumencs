package com.lumencs.service;

import com.lumencs.model.entity.KbChunk;
import com.lumencs.mapper.KbChunkMapper;
import com.lumencs.model.entity.KbDocument;
import com.lumencs.mapper.KbDocumentMapper;
import com.lumencs.knowledge.TextChunker;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumencs.exception.BizException;
import com.lumencs.rag.RagClient;
import com.lumencs.rag.RagHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return ingest(title, source, content, TextChunker.Options.defaults());
    }

    @Transactional
    public KbDocument ingest(String title, String source, String content, TextChunker.Options options) {
        KbDocument doc = new KbDocument();
        doc.setTitle(title);
        doc.setSource(source == null || source.isBlank() ? title : source);
        doc.setContent(content);
        doc.setStatus("INDEXING");
        doc.setChunkCount(0);
        documentMapper.insert(doc);
        indexChunks(doc, content, options);
        return doc;
    }

    /** 换 embedding 模型/维度后，用库里已有正文重新切分并写入 Qdrant。 */
    @Transactional
    public int reindexAll() {
        int n = 0;
        for (KbDocument doc : listDocuments()) {
            reindex(doc.getId());
            n++;
        }
        return n;
    }

    @Transactional
    public KbDocument reindex(Long id) {
        KbDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        ragClient.deleteDocument(id);
        chunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocumentId, id));
        indexChunks(doc, doc.getContent(), TextChunker.Options.defaults());
        return doc;
    }

    private void indexChunks(KbDocument doc, String content, TextChunker.Options options) {
        List<TextChunker.Piece> parts = TextChunker.split(content, options);
        List<KbChunk> chunks = new ArrayList<>();
        List<Map<String, Object>> points = new ArrayList<>();
        int order = 0;
        for (TextChunker.Piece part : parts) {
            KbChunk chunk = new KbChunk();
            chunk.setId(UUID.randomUUID().toString());
            chunk.setDocumentId(doc.getId());
            chunk.setContent(part.context());
            chunk.setSource(doc.getSource());
            chunk.setSortOrder(order++);
            chunkMapper.insert(chunk);
            chunks.add(chunk);

            Map<String, Object> payload = new HashMap<>();
            payload.put("document_id", doc.getId());
            payload.put("content", part.context());
            payload.put("source", doc.getSource());
            payload.put("title", doc.getTitle());
            points.add(Map.of(
                    "id", chunk.getId(),
                    "text", part.retrieval(),
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
    }

    public KbDocument getDocument(Long id) {
        KbDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        return doc;
    }

    public List<KbChunk> listChunks(Long documentId) {
        return chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getDocumentId, documentId)
                .orderByAsc(KbChunk::getSortOrder));
    }

    public List<String> preview(String content, TextChunker.Options options) {
        return TextChunker.preview(content, options);
    }

    @Transactional
    public void delete(Long id) {
        KbDocument doc = getDocument(id);
        try {
            ragClient.deleteDocument(id);
        } catch (Exception e) {
            log.warn("vector delete failed for doc {}: {}", id, e.getMessage());
        }
        chunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocumentId, id));
        documentMapper.deleteById(doc.getId());
    }

    public List<RagHit> search(String query) {
        return search(query, null);
    }

    public KbDocument findByBlogSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        return documentMapper.selectOne(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getSource, "blog:" + slug.trim())
                .last("LIMIT 1"));
    }

    /** 发布后把正文写入本仓知识库；已有 blog:{slug} 则覆盖再切分。草稿不入库，避免公聊检索到未发布内容。 */
    @Transactional
    public KbDocument upsertBlog(String title, String slug, String content) {
        KbDocument existing = findByBlogSlug(slug);
        if (existing == null) {
            return ingest(title, "blog:" + slug.trim(), content);
        }
        existing.setTitle(title);
        existing.setContent(content);
        documentMapper.updateById(existing);
        ragClient.deleteDocument(existing.getId());
        chunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocumentId, existing.getId()));
        indexChunks(existing, content, TextChunker.Options.defaults());
        return existing;
    }

    public List<RagHit> search(String query, Long documentId) {
        try {
            List<RagHit> hits = ragClient.search(query, topK, documentId);
            if (!hits.isEmpty()) {
                return hits;
            }
        } catch (Exception e) {
            log.warn("vector search failed, fallback to keyword", e);
        }
        return keywordSearch(query, documentId);
    }

    private List<RagHit> keywordSearch(String query, Long documentId) {
        Set<String> terms = Set.of(query.toLowerCase().split("[\\s,，。！？?]+"));
        LambdaQueryWrapper<KbChunk> wrapper = new LambdaQueryWrapper<>();
        if (documentId != null) {
            wrapper.eq(KbChunk::getDocumentId, documentId);
        }
        List<KbChunk> chunks = chunkMapper.selectList(wrapper);
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
