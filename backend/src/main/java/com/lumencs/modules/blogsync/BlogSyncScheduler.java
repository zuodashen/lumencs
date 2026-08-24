package com.lumencs.modules.blogsync;

import com.lumencs.service.BlogSyncService;
import com.lumencs.modules.mcp.BlogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 博客定时同步：按 {@code lumencs.blog.sync-cron}（默认每 6 小时）拉取博客公开文章进知识库。
 * 未配置 BLOG_BASE_URL 时静默跳过；控制台手动按钮仍可用。
 */
@Component
public class BlogSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(BlogSyncScheduler.class);

    private final BlogSyncService blogSyncService;
    private final BlogClient blogClient;

    public BlogSyncScheduler(BlogSyncService blogSyncService, BlogClient blogClient) {
        this.blogSyncService = blogSyncService;
        this.blogClient = blogClient;
    }

    @Scheduled(cron = "${lumencs.blog.sync-cron}")
    public void sync() {
        if (!blogClient.enabled()) {
            return;
        }
        try {
            Map<String, Object> result = blogSyncService.sync();
            log.info("blog scheduled sync finished: {}", result);
        } catch (Exception e) {
            log.warn("blog scheduled sync failed: {}", e.getMessage());
        }
    }
}
