package com.lumencs.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 固定窗口限流：INCR + EXPIRE，按窗口桶计数。
 * Redis 不可用时进程内 ConcurrentHashMap 兜底。
 */
@Component
public class RateLimitService {

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final Map<String, Integer> localCounters = new ConcurrentHashMap<>();

    public RateLimitService(StringRedisTemplate redis,
                            @Value("${lumencs.ratelimit.key-prefix}") String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
    }

    /**
     * @return true=放行，false=超限
     */
    public boolean allow(String key, int capacity, int windowSeconds) {
        long bucket = System.currentTimeMillis() / (windowSeconds * 1000L);
        String redisKey = keyPrefix + ":" + key + ":" + bucket;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                redis.expire(redisKey, Duration.ofSeconds(windowSeconds));
            }
            return count == null || count <= capacity;
        } catch (Exception e) {
            // Redis 不可用：进程内固定窗口兜底
            String localKey = key + ":" + bucket;
            int c = localCounters.merge(localKey, 1, Integer::sum);
            if (localCounters.size() > 10_000) {
                localCounters.clear();
            }
            return c <= capacity;
        }
    }
}
