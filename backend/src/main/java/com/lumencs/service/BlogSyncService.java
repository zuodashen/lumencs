package com.lumencs.service;

import com.lumencs.exception.BizException;
import com.lumencs.model.entity.KbDocument;
import com.lumencs.modules.mcp.BlogClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        List<Map<String, Object>> articles = fetchAllPublished();
        Set<String> existing = knowledgeService.listDocuments().stream()
                .map(KbDocument::getSource)
                .collect(Collectors.toSet());
        int created = 0;
        int updated = 0;
        for (Map<String, Object> article : articles) {
            String slug = slugOf(article);
            if (slug.isBlank()) {
                continue;
            }
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

    public Map<String, Object> syncSlug(String slug) {
        if (!blogClient.enabled()) {
            throw new BizException("未配置 lumencs.blog.base-url");
        }
        String key = slug == null ? "" : slug.trim();
        if (key.isBlank()) {
            throw new BizException("缺少文章 slug");
        }
        Map<String, Object> detail = blogClient.getArticle(key);
        if (detail.isEmpty()) {
            throw new BizException("博客前台没有这篇已发布文章：" + key);
        }
        String title = String.valueOf(detail.getOrDefault("title", key));
        String content = String.valueOf(detail.getOrDefault("content",
                detail.getOrDefault("summary", title)));
        boolean existed = knowledgeService.findByBlogSlug(key) != null;
        knowledgeService.upsertBlog(title, key, content);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("slug", key);
        result.put("title", title);
        result.put("created", !existed);
        result.put("updated", existed);
        result.put("url", blogClient.articleUrl(key));
        return result;
    }

    private List<Map<String, Object>> fetchAllPublished() {
        List<Map<String, Object>> all = new ArrayList<>();
        for (int page = 1; page <= 40; page++) {
            List<Map<String, Object>> batch = blogClient.listArticles("", page, 50);
            if (batch.isEmpty()) {
                break;
            }
            all.addAll(batch);
            if (batch.size() < 50) {
                break;
            }
        }
        return all;
    }

    private static String slugOf(Map<String, Object> article) {
        Object slug = article.get("slug");
        if (slug != null && !String.valueOf(slug).isBlank() && !"null".equals(String.valueOf(slug))) {
            return String.valueOf(slug).trim();
        }
        Object id = article.get("id");
        return id == null ? "" : String.valueOf(id).trim();
    }
}
