package com.lumencs.controller;

import com.lumencs.common.R;
import com.lumencs.modules.blogsync.BlogSyncSettings;
import com.lumencs.service.BlogSyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/blog")
public class BlogSyncController {

    private final BlogSyncService blogSyncService;
    private final BlogSyncSettings settings;

    public BlogSyncController(BlogSyncService blogSyncService, BlogSyncSettings settings) {
        this.blogSyncService = blogSyncService;
        this.settings = settings;
    }

    @GetMapping("/settings")
    public R<Map<String, Object>> settings() {
        return R.success(settings.snapshot());
    }

    @PatchMapping("/settings")
    public R<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> body) {
        Object flag = body == null ? null : body.get("syncEnabled");
        if (flag instanceof Boolean enabled) {
            settings.setEnabled(enabled);
        } else if (flag != null) {
            settings.setEnabled(Boolean.parseBoolean(String.valueOf(flag)));
        }
        return R.success(settings.snapshot());
    }

    @PostMapping("/sync")
    public R<Map<String, Object>> sync() {
        return R.success(blogSyncService.sync());
    }

    @PostMapping("/sync/{slug}")
    public R<Map<String, Object>> syncSlug(@PathVariable String slug) {
        return R.success(blogSyncService.syncSlug(slug));
    }
}
