package com.lumencs.controller;

import com.lumencs.common.ApiResponse;
import com.lumencs.common.R;
import com.lumencs.model.entity.KbDocument;
import com.lumencs.service.KnowledgeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/hub")
public class PublicHubController {

    private final KnowledgeService knowledgeService;

    public PublicHubController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/scope")
    public R<Map<String, Object>> scope(@RequestParam String slug) {
        KbDocument doc = knowledgeService.findByBlogSlug(slug);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("slug", slug);
        body.put("ready", doc != null);
        body.put("title", doc == null ? slug : doc.getTitle());
        return ApiResponse.ok(body);
    }
}
