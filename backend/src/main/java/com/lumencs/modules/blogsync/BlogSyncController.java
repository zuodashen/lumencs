package com.lumencs.modules.blogsync;

import com.lumencs.common.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/blog")
public class BlogSyncController {

    private final BlogSyncService blogSyncService;

    public BlogSyncController(BlogSyncService blogSyncService) {
        this.blogSyncService = blogSyncService;
    }

    @PostMapping("/sync")
    public R<Map<String, Object>> sync() {
        return R.success(blogSyncService.sync());
    }
}
