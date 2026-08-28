package com.lumencs.service;

import com.lumencs.exception.BizException;
import com.lumencs.model.entity.KbDocument;
import com.lumencs.modules.mcp.BlogClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BlogSyncService {

    private final BlogClient blogClient;
    private final KnowledgeService knowledgeService;

    public BlogSyncService(BlogClient blogClient, KnowledgeService knowledgeService) {
        this.blogClient = blogClient;
        this.knowledgeService = knowledgeService;
    }

    public Map<String, Object> sync() {
        if (!blogClient.enabled()) {
            throw new BizException("未配置 lumencs.blog.base-url，博客仓无需改代码；配置公开 API 地址后再同步");
        }
        List<Map<String, Object>> articles = blogClient.search("");
        Set<String> existing = knowledgeService.listDocuments().stream()
                .map(KbDocument::getSource)
                .collect(Collectors.toSet());
        int created = 0;
        int updated = 0;
        for (Map<String, Object> article : articles) {
            String slug = String.valueOf(article.getOrDefault("slug", article.get("id")));
            String source = "blog:" + slug;
            Map<String, Object> detail = blogClient.getArticle(slug);
            String title = String.valueOf(detail.getOrDefault("title", article.getOrDefault("title", slug)));
            String content = String.valueOf(detail.getOrDefault("content",
                    article.getOrDefault("summary", title)));
            if (existing.contains(source)) {
                knowledgeService.upsertBlog(title, slug, content);
                updated++;
                continue;
            }
            knowledgeService.ingest(title, source, content);
            created++;
            existing.add(source);
        }
        return Map.of("fetched", articles.size(), "created", created, "updated", updated,
                "skipped", Math.max(0, articles.size() - created - updated));
    }
}
