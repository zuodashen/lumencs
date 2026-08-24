package com.lumencs.knowledge;

import com.lumencs.common.ApiResponse;
import com.lumencs.common.PageWrapper;
import com.lumencs.common.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /** 分页列表（VO 出，不含大段正文）。 */
    @GetMapping
    public R<PageWrapper<DocumentVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                           @RequestParam(defaultValue = "10") long pageSize) {
        var page = knowledgeService.listDocumentsPage(pageNum, pageSize);
        List<DocumentVO> records = page.getRecords().stream().map(DocumentVO::from).toList();
        return ApiResponse.ok(PageWrapper.of(page.getTotal(), pageNum, pageSize, records));
    }

    @PostMapping
    public R<DocumentVO> create(@Valid @RequestBody CreateDocumentRequest request) {
        return ApiResponse.ok(DocumentVO.from(knowledgeService.ingest(
                request.getTitle(), request.getSource(), request.getContent())));
    }

    @Data
    public static class CreateDocumentRequest {
        @NotBlank
        private String title;
        private String source;
        @NotBlank
        private String content;
    }
}
