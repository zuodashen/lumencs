package com.lumencs.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 分布式锁：SET key token NX EX（加锁）+ Lua 比对删除（释放）。
 * Redis 不可用时退化为进程内 ConcurrentHashMap 兜底，保证单机 demo 可用。
 */
@Component
public class RedisLockService {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final Map<String, String> localLocks = new ConcurrentHashMap<>();

    public RedisLockService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryLock(String key, String token, Duration ttl) {
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(key, token, ttl);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            // Redis 不可用：进程内兜底
            return localLocks.putIfAbsent(key, token) == null;
        }
    }

    public void unlock(String key, String token) {
        try {
            redis.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
        } catch (Exception ignored) {
            // Redis 不可用，忽略
        }
        localLocks.remove(key, token);
    }

    public static String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
