package com.lumencs.modules.mcp;

import com.lumencs.lock.RedisLockService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * 写工具同一张卡片只派发一次。超时后结果未知，不能再 POST。
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

    public WriteDispatchGuard(RedisLockService lockService) {
        this.lockService = lockService;
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
}
