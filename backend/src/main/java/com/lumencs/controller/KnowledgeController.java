package com.lumencs.controller;

import com.lumencs.common.ApiResponse;
import com.lumencs.common.PageWrapper;
import com.lumencs.common.R;
import com.lumencs.knowledge.TextChunker;
import com.lumencs.model.dto.ChunkPreviewRequest;
import com.lumencs.model.dto.CreateDocumentRequest;
import com.lumencs.model.dto.RecallTestRequest;
import com.lumencs.model.entity.KbChunk;
import com.lumencs.model.entity.KbDocument;
import com.lumencs.model.vo.DocumentVO;
import com.lumencs.rag.RagHit;
import com.lumencs.service.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping
    public R<PageWrapper<DocumentVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                           @RequestParam(defaultValue = "10") long pageSize) {
        var page = knowledgeService.listDocumentsPage(pageNum, pageSize);
        List<DocumentVO> records = page.getRecords().stream().map(DocumentVO::from).toList();
        return ApiResponse.ok(PageWrapper.of(page.getTotal(), pageNum, pageSize, records));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        KbDocument doc = knowledgeService.getDocument(id);
        List<Map<String, Object>> chunks = knowledgeService.listChunks(id).stream()
                .map(KnowledgeController::chunkView)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("document", DocumentVO.from(doc));
        body.put("content", doc.getContent());
        body.put("chunks", chunks);
        return ApiResponse.ok(body);
    }

    @PostMapping
    public R<DocumentVO> create(@Valid @RequestBody CreateDocumentRequest request) {
        return ApiResponse.ok(DocumentVO.from(knowledgeService.ingest(
                request.getTitle(), request.getSource(), request.getContent(), options(request))));
    }

    @PostMapping("/preview")
    public R<Map<String, Object>> preview(@RequestBody ChunkPreviewRequest request) {
        String content = request.getContent() == null ? "" : request.getContent();
        List<String> chunks = knowledgeService.preview(content, options(
                request.getCollapseWhitespace(), request.getParagraphSplit(),
                request.getParentMax(), request.getChildMax()));
        return ApiResponse.ok(Map.of("count", chunks.size(), "chunks", chunks));
    }

    @PostMapping("/recall")
    public R<List<RagHit>> recall(@Valid @RequestBody RecallTestRequest request) {
        return ApiResponse.ok(knowledgeService.search(request.getQuery(), request.getDocumentId()));
    }

    @PostMapping("/reindex")
    public R<Map<String, Integer>> reindex() {
        return ApiResponse.ok(Map.of("reindexed", knowledgeService.reindexAll()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ApiResponse.ok(null);
    }

    private static TextChunker.Options options(CreateDocumentRequest request) {
        return options(request.getCollapseWhitespace(), request.getParagraphSplit(),
                request.getParentMax(), request.getChildMax());
    }

    private static TextChunker.Options options(Boolean collapse, Boolean paragraph, Integer parentMax, Integer childMax) {
        TextChunker.Options d = TextChunker.Options.defaults();
        return new TextChunker.Options(
                collapse == null ? d.collapseWhitespace() : collapse,
                paragraph == null ? d.paragraph() : paragraph,
                parentMax == null ? d.parentMax() : parentMax,
                childMax == null ? d.childMax() : childMax,
                d.overlap()
        );
    }

    private static Map<String, Object> chunkView(KbChunk chunk) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", chunk.getId());
        row.put("sortOrder", chunk.getSortOrder());
        row.put("charCount", chunk.getContent() == null ? 0 : chunk.getContent().length());
        row.put("content", chunk.getContent());
        return row;
    }
}
