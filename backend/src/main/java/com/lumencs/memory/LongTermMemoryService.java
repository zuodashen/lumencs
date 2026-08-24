package com.lumencs.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 长期画像：跨会话记住口味/工位。借鉴 zbp-ai「记忆预填表单」，不做校园四层群体记忆。
 */
@Service
public class LongTermMemoryService {

    private static final Duration TTL = Duration.ofDays(14);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, Object>> fallback = new ConcurrentHashMap<>();

    public LongTermMemoryService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> profile(String userLabel) {
        String key = key(userLabel);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return new HashMap<>(fallback.getOrDefault(key, Map.of()));
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>(fallback.getOrDefault(key, Map.of()));
        }
    }

    public void remember(String userLabel, Map<String, Object> facts) {
        if (userLabel == null || userLabel.isBlank() || facts == null || facts.isEmpty()) {
            return;
        }
        Map<String, Object> merged = profile(userLabel);
        facts.forEach((k, v) -> {
            if (v != null && !v.toString().isBlank()) {
                merged.put(k, v);
            }
        });
        String key = key(userLabel);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(merged), TTL);
        } catch (Exception e) {
            fallback.put(key, merged);
        }
    }

    private String key(String userLabel) {
        return "lumencs:profile:" + userLabel;
    }
}
