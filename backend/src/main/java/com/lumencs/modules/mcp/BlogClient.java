package com.lumencs.modules.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 只读调用 lightdiary 公开 API。博客仓库不改代码；base-url 未配置则返回空。
 */
@Component
public class BlogClient {

    private static final Logger log = LoggerFactory.getLogger(BlogClient.class);
    private final RestClient client;
    private final boolean enabled;
    private final String publicWebUrl;

    public BlogClient(
            @Value("${lumencs.blog.base-url:}") String baseUrl,
            @Value("${lumencs.blog.public-web-url:}") String publicWebUrl) {
        this.enabled = baseUrl != null && !baseUrl.isBlank();
        this.publicWebUrl = publicWebUrl == null ? "" : publicWebUrl.replaceAll("/$", "");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(8000);
        this.client = RestClient.builder()
                .baseUrl(enabled ? baseUrl : "http://127.0.0.1")
                .requestFactory(factory)
                .build();
    }

    public boolean enabled() {
        return enabled;
    }

    public String publicWebUrl() {
        return publicWebUrl;
    }

    public String articleUrl(String slug) {
        if (publicWebUrl.isBlank() || slug == null || slug.isBlank()) {
            return "";
        }
        return publicWebUrl + "/post/" + slug.trim();
    }

    public List<Map<String, Object>> listTags() {
        return listArray("/api/tags");
    }

    public List<Map<String, Object>> listCategories() {
        return listArray("/api/categories");
    }

    public List<Map<String, Object>> listBookmarkGroups() {
        return listArray("/api/bookmarks");
    }

    public Map<String, Object> getArticle(String slug) {
        if (!enabled || slug == null || slug.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> body = client.get()
                    .uri("/api/articles/{slug}", slug.trim())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (body == null) {
                return Map.of();
            }
            Object data = body.get("data");
            if (data instanceof Map<?, ?> article) {
                Map<String, Object> copy = new java.util.LinkedHashMap<>();
                article.forEach((k, v) -> copy.put(String.valueOf(k), v));
                return copy;
            }
            return Map.of();
        } catch (Exception e) {
            log.warn("blog article skipped: {}", e.getMessage());
            return Map.of();
        }
    }

    public List<Map<String, Object>> search(String query) {
        return listArticles(query, 1, 5);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listArticles(String query, int pageNum, int pageSize) {
        if (!enabled) {
            return List.of();
        }
        int page = Math.max(pageNum, 1);
        int size = Math.min(Math.max(pageSize, 1), 50);
        try {
            Map<String, Object> body = client.get()
                    .uri(uri -> uri.path("/api/articles")
                            .queryParam("pageNum", page)
                            .queryParam("pageSize", size)
                            .queryParam("keyword", query == null ? "" : query)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (body == null) {
                return List.of();
            }
            Object data = body.get("data");
            if (data instanceof Map<?, ?> pageBody) {
                Object list = pageBody.get("list");
                if (list instanceof List<?> items) {
                    return items.stream()
                            .filter(Map.class::isInstance)
                            .map(item -> (Map<String, Object>) item)
                            .toList();
                }
            }
            return List.of();
        } catch (Exception e) {
            log.warn("blog search skipped: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listArray(String path) {
        if (!enabled) {
            return List.of();
        }
        try {
            Map<String, Object> body = client.get()
                    .uri(path)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (body == null) {
                return List.of();
            }
            Object data = body.get("data");
            if (data instanceof List<?> items) {
                return items.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> (Map<String, Object>) item)
                        .toList();
            }
            return List.of();
        } catch (Exception e) {
            log.warn("blog {} skipped: {}", path, e.getMessage());
            return List.of();
        }
    }
}
