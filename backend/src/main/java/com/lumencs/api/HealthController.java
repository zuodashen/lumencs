package com.lumencs.api;

import com.lumencs.common.ApiResponse;
import com.lumencs.common.R;
import com.lumencs.rag.RagClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final RagClient ragClient;
    private final StringRedisTemplate redisTemplate;

    public HealthController(RagClient ragClient, StringRedisTemplate redisTemplate) {
        this.ragClient = ragClient;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/api/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", "lumencs-backend");
        payload.put("status", "healthy");
        payload.put("rag", ragClient.healthy() ? "up" : "down");
        payload.put("redis", redisUp() ? "up" : "down");
        return ApiResponse.ok(payload);
    }

    private boolean redisUp() {
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            return pong != null;
        } catch (Exception e) {
            return false;
        }
    }
}
