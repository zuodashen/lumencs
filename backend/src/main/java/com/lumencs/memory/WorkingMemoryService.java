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
 * 工作记忆：当前办事流程的槽位、待提交卡片、最近一次意图。
 * Redis Hash，TTL 30min；失败则进程内兜底。
 */
@Service
public class WorkingMemoryService {

    private static final Duration TTL = Duration.ofMinutes(30);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, String>> fallback = new ConcurrentHashMap<>();

    public WorkingMemoryService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void put(String sessionId, String field, Object value) {
        String json;
        try {
            json = value instanceof String s ? s : objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            json = String.valueOf(value);
        }
        try {
            String redisKey = key(sessionId);
            redisTemplate.opsForHash().put(redisKey, field, json);
            redisTemplate.expire(redisKey, TTL);
        } catch (Exception e) {
            fallback.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(field, json);
        }
    }

    public String getString(String sessionId, String field) {
        String raw = raw(sessionId, field);
        return raw == null ? "" : raw;
    }

    public Map<String, Object> getMap(String sessionId, String field) {
        String raw = raw(sessionId, field);
        if (raw == null || raw.isBlank() || "{}".equals(raw)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public void mergeSlots(String sessionId, Map<String, Object> values) {
        Map<String, Object> slots = getMap(sessionId, "slots");
        if (values != null) {
            slots.putAll(values);
        }
        put(sessionId, "slots", slots);
    }

    public Map<String, Object> snapshot(String sessionId) {
        Map<String, Object> snap = new HashMap<>();
        snap.put("intent", getString(sessionId, "intent"));
        snap.put("workflow", getString(sessionId, "workflow"));
        snap.put("pendingCardId", getString(sessionId, "pendingCardId"));
        snap.put("slots", getMap(sessionId, "slots"));
        return snap;
    }

    public void clearWorkflow(String sessionId) {
        put(sessionId, "workflow", "");
        put(sessionId, "pendingCardId", "");
        put(sessionId, "slots", Map.of());
    }

    private String raw(String sessionId, String field) {
        try {
            Object v = redisTemplate.opsForHash().get(key(sessionId), field);
            if (v != null) {
                return v.toString();
            }
        } catch (Exception ignored) {
            // fallback
        }
        Map<String, String> local = fallback.get(sessionId);
        return local == null ? null : local.get(field);
    }

    private String key(String sessionId) {
        return "lumencs:working:" + sessionId;
    }
}
