package com.lumencs.modules.blogsync;

import com.lumencs.modules.mcp.BlogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 定时同步开关存在 Redis，关掉后不必改 .env / 重启。
 * 未写过 Redis 时回落到 {@code lumencs.blog.sync-enabled}。
 */
@Component
public class BlogSyncSettings {

    private static final Logger log = LoggerFactory.getLogger(BlogSyncSettings.class);
    static final String REDIS_KEY = "lumencs:blog:sync-enabled";

    private final StringRedisTemplate redis;
    private final BlogClient blogClient;
    private final boolean defaultEnabled;
    private final String syncCron;

    public BlogSyncSettings(
            StringRedisTemplate redis,
            BlogClient blogClient,
            @Value("${lumencs.blog.sync-enabled:true}") boolean defaultEnabled,
            @Value("${lumencs.blog.sync-cron:0 0 */6 * * *}") String syncCron) {
        this.redis = redis;
        this.blogClient = blogClient;
        this.defaultEnabled = defaultEnabled;
        this.syncCron = syncCron == null ? "" : syncCron;
    }

    public boolean isEnabled() {
        try {
            String raw = redis.opsForValue().get(REDIS_KEY);
            if (raw == null || raw.isBlank()) {
                return defaultEnabled;
            }
            return "true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim());
        } catch (Exception e) {
            log.warn("blog sync flag read failed, fallback to env: {}", e.getMessage());
            return defaultEnabled;
        }
    }

    public void setEnabled(boolean enabled) {
        redis.opsForValue().set(REDIS_KEY, enabled ? "true" : "false");
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("syncEnabled", isEnabled());
        body.put("syncCron", syncCron);
        body.put("blogConfigured", blogClient.enabled());
        return body;
    }
}
