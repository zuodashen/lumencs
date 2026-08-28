package com.lumencs.controller;

import com.lumencs.modules.mcp.BlogAdminClient;
import com.lumencs.modules.mcp.BlogClient;
import com.lumencs.modules.mcp.McpToolServer;
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
    private final BlogAdminClient blogAdminClient;

    public ToolController(McpToolServer mcpToolServer, BlogClient blogClient, BlogAdminClient blogAdminClient) {
        this.mcpToolServer = mcpToolServer;
        this.blogClient = blogClient;
        this.blogAdminClient = blogAdminClient;
    }

    @GetMapping
    public R<Map<String, Object>> overview() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tools", mcpToolServer.listTools());
        body.put("recentLogs", mcpToolServer.recentLogs());
        body.put("blogEnabled", blogClient.enabled());
        body.put("blogWriteEnabled", blogAdminClient.writeReady());
        return R.success(body);
    }
}
