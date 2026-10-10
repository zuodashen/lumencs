package com.lumencs.modules.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumencs.lock.RedisLockService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 写工具同一张卡片只派发一次。超时后结果未知，不能再 POST。
 * 首次结果写入 Redis，重复提交回放同一份结果（begin_or_replay）。
 */
@Component
public class WriteDispatchGuard {

    static final Set<String> WRITE_TOOLS = Set.of(
            "ticket_create",
            "ticket_update",
            "memo_save",
            "tea_order",
            "blog_article_upsert",
            "blog_bookmark_create",
            "blog_tag_create",
            "blog_sync_slug"
    );

    private static final Duration TTL = Duration.ofMinutes(15);
    private final RedisLockService lockService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Map<String, String> localResults = new ConcurrentHashMap<>();

    public WriteDispatchGuard(RedisLockService lockService, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.lockService = lockService;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public static boolean isWrite(String tool) {
        return tool != null && WRITE_TOOLS.contains(tool);
    }

    /** @return true 表示本轮可以执行；false 表示已经派发过 */
    public boolean claim(String sessionId, String tool, String idempotencyKey) {
        if (sessionId == null || sessionId.isBlank() || idempotencyKey == null || idempotencyKey.isBlank()) {
            return true;
        }
        String key = "lumencs:write:" + sessionId + ":" + tool + ":" + idempotencyKey;
        return lockService.tryLock(key, "1", TTL);
    }

    public void remember(String sessionId, String tool, String idempotencyKey, Map<String, Object> result) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || result == null) {
            return;
        }
        String key = resultKey(sessionId, tool, idempotencyKey);
        try {
            String json = objectMapper.writeValueAsString(result);
            redis.opsForValue().set(key, json, TTL);
            localResults.put(key, json);
        } catch (Exception e) {
            try {
                localResults.put(key, objectMapper.writeValueAsString(result));
            } catch (Exception ignored) {
                // skip
            }
        }
    }

    public Map<String, Object> replay(String sessionId, String tool, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String key = resultKey(sessionId, tool, idempotencyKey);
        String json = null;
        try {
            json = redis.opsForValue().get(key);
        } catch (Exception ignored) {
            // fallback
        }
        if (json == null) {
            json = localResults.get(key);
        }
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private static String resultKey(String sessionId, String tool, String idempotencyKey) {
        return "lumencs:write:result:" + sessionId + ":" + tool + ":" + idempotencyKey;
    }
}
