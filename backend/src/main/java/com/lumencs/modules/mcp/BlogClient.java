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

    public BlogClient(@Value("${lumencs.blog.base-url:}") String baseUrl) {
        this.enabled = baseUrl != null && !baseUrl.isBlank();
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

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> search(String query) {
        if (!enabled) {
            return List.of();
        }
        try {
            Map<String, Object> body = client.get()
                    .uri(uri -> uri.path("/api/articles")
                            .queryParam("pageNum", 1)
                            .queryParam("pageSize", 5)
                            .queryParam("keyword", query == null ? "" : query)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (body == null) {
                return List.of();
            }
            Object data = body.get("data");
            if (data instanceof Map<?, ?> page) {
                Object list = page.get("list");
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
}
