package com.lumencs.modules.mcp;

import com.lumencs.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/tools")
public class ToolController {

    private final McpToolServer mcpToolServer;
    private final BlogClient blogClient;

    public ToolController(McpToolServer mcpToolServer, BlogClient blogClient) {
        this.mcpToolServer = mcpToolServer;
        this.blogClient = blogClient;
    }

    @GetMapping
    public R<Map<String, Object>> overview() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tools", mcpToolServer.listTools());
        body.put("recentLogs", mcpToolServer.recentLogs());
        body.put("blogEnabled", blogClient.enabled());
        return R.success(body);
    }
}
