package com.lumencs.modules.blogsync;

import com.lumencs.exception.BizException;
import com.lumencs.knowledge.KbDocument;
import com.lumencs.knowledge.KnowledgeService;
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
        for (Map<String, Object> article : articles) {
            String slug = String.valueOf(article.getOrDefault("slug", article.get("id")));
            String source = "blog:" + slug;
            if (existing.contains(source)) {
                continue;
            }
            String title = String.valueOf(article.getOrDefault("title", slug));
            String summary = String.valueOf(article.getOrDefault("summary", title));
            knowledgeService.ingest(title, source, summary);
            created++;
        }
        return Map.of("fetched", articles.size(), "created", created, "skipped", articles.size() - created);
    }
}
