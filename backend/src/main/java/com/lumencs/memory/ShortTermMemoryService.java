package com.lumencs.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShortTermMemoryService {

    private static final int MAX_TURNS = 20;
    private static final Duration TTL = Duration.ofMinutes(30);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, List<Map<String, String>>> fallback = new ConcurrentHashMap<>();

    public ShortTermMemoryService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void addMessage(String sessionId, String role, String content) {
        Map<String, String> message = Map.of(
                "role", role,
                "content", content,
                "timestamp", LocalDateTime.now().toString()
        );
        try {
            String key = key(sessionId);
            redisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(message));
            redisTemplate.opsForList().trim(key, -MAX_TURNS, -1);
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            fallback.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
            List<Map<String, String>> list = fallback.get(sessionId);
            if (list.size() > MAX_TURNS) {
                fallback.put(sessionId, new ArrayList<>(list.subList(list.size() - MAX_TURNS, list.size())));
            }
        }
    }

    public List<Map<String, Object>> history(String sessionId) {
        try {
            List<String> raw = redisTemplate.opsForList().range(key(sessionId), 0, -1);
            if (raw == null) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (String json : raw) {
                result.add(objectMapper.readValue(json, Map.class));
            }
            return result;
        } catch (JsonProcessingException | RuntimeException e) {
            List<Map<String, String>> local = fallback.getOrDefault(sessionId, List.of());
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, String> item : local) {
                result.add(new HashMap<>(item));
            }
            return result;
        }
    }

    public String contextWindow(String sessionId) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> msg : history(sessionId)) {
            sb.append(msg.getOrDefault("role", "user"))
                    .append(": ")
                    .append(msg.getOrDefault("content", ""))
                    .append('\n');
        }
        return sb.toString();
    }

    private String key(String sessionId) {
        return "lumencs:short_term:" + sessionId;
    }
}
