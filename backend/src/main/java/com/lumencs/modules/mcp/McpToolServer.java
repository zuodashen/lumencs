package com.lumencs.modules.mcp;

import com.lumencs.model.entity.ToolLog;
import com.lumencs.mapper.ToolLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumencs.service.KnowledgeService;
import com.lumencs.service.BlogSyncService;
import com.lumencs.modules.panwatch.StockInsightService;
import com.lumencs.rag.RagHit;
import com.lumencs.model.entity.Ticket;
import com.lumencs.model.entity.TicketStatus;
import com.lumencs.service.TicketService;
import com.lumencs.tracing.AgentTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MCP 风格工具注册表：意图进入办事流程后，由 WorkflowAgent 按 schema 调用。
 * 工具是真 handler，不是空 executed；每次调用写入 cs_tool_log（控制台可见）。
 */
@Component
public class McpToolServer {

    private static final Logger log = LoggerFactory.getLogger(McpToolServer.class);

    private final TicketService ticketService;
    private final KnowledgeService knowledgeService;
    private final BlogClient blogClient;
    private final BlogAdminClient blogAdminClient;
    private final BlogSyncService blogSyncService;
    private final StockInsightService stockInsightService;
    private final AgentTracer tracer;
    private final ToolLogMapper toolLogMapper;
    private final ObjectMapper objectMapper;
    /** 进程内最近调用（兜底 / 快速查看，DB 持久化为主） */
    private final List<Map<String, Object>> callLog = new CopyOnWriteArrayList<>();

    public McpToolServer(
            TicketService ticketService,
            KnowledgeService knowledgeService,
            BlogClient blogClient,
            BlogAdminClient blogAdminClient,
            BlogSyncService blogSyncService,
            StockInsightService stockInsightService,
            AgentTracer tracer,
            ToolLogMapper toolLogMapper,
            ObjectMapper objectMapper) {
        this.ticketService = ticketService;
        this.knowledgeService = knowledgeService;
        this.blogClient = blogClient;
        this.blogAdminClient = blogAdminClient;
        this.blogSyncService = blogSyncService;
        this.stockInsightService = stockInsightService;
        this.tracer = tracer;
        this.toolLogMapper = toolLogMapper;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool("ticket_create", "创建待办事项", List.of("title", "description", "priority", "session_id", "user_label")));
        tools.add(tool("ticket_query", "按编号查询待办", List.of("ticket_no")));
        tools.add(tool("ticket_list", "列出最近待办及状态", List.of()));
        tools.add(tool("ticket_update", "按编号改待办状态", List.of("ticket_no", "status")));
        tools.add(tool("memo_save", "把备忘写入个人知识库", List.of("title", "content")));
        tools.add(tool("kb_search", "检索内部知识库", List.of("query")));
        tools.add(tool("blog_search", "检索个人博客已发布文章", List.of("query")));
        tools.add(tool("blog_list", "列出已发布博客，可在对话里点同步", List.of("query")));
        tools.add(tool("blog_bookmarks", "列出博客书签分组", List.of()));
        tools.add(tool("blog_get", "按 slug 读取一篇已发布文章", List.of("slug")));
        tools.add(tool("blog_sync_slug", "把一篇已发布博客同步进本仓知识库", List.of("slug")));
        tools.add(tool("blog_article_upsert", "卡片确认后写入博客文章（默认草稿）", List.of("title", "summary", "content", "category", "tags", "action")));
        tools.add(tool("blog_bookmark_create", "卡片确认后添加博客书签", List.of("name", "link", "description", "category")));
        tools.add(tool("blog_tag_create", "卡片确认后新建文章标签", List.of("name")));
        tools.add(tool("stock_quote", "查盯盘侠行情 / K 线 / 技术摘要", List.of("query")));
        tools.add(tool("order_query", "查询演示订单（mock）", List.of("order_id")));
        tools.add(tool("tea_order", "工位奶茶下单（演示）", List.of("drink", "size", "sweetness", "ice", "topping", "count", "desk")));
        return tools;
    }

    public Map<String, Object> call(String sessionId, String name, Map<String, Object> args) {
        long start = System.currentTimeMillis();
        Map<String, Object> result;
        boolean success = true;
        try {
            result = tracer.trace(sessionId, "mcp", name, args == null ? Map.of() : args, () -> dispatch(name, args == null ? Map.of() : args));
        } catch (Exception e) {
            success = false;
            result = Map.of("success", false, "error", e.getMessage() == null ? "tool failed" : e.getMessage());
        }
        long duration = System.currentTimeMillis() - start;

        // 调用日志：DB 持久化（主）+ 进程内最近（兜底）
        persistLog(sessionId, name, args, result, success, duration);

        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("tool", name);
        logEntry.put("success", success);
        logEntry.put("durationMs", duration);
        logEntry.put("time", LocalDateTime.now().toString());
        logEntry.put("args", redactArgs(args));
        callLog.add(0, logEntry);
        if (callLog.size() > 100) {
            callLog.remove(callLog.size() - 1);
        }
        return result;
    }

    /** DB 最近 50 条调用日志；DB 不可用时退回进程内日志。 */
    public List<Object> recentLogs() {
        try {
            List<ToolLog> rows = toolLogMapper.selectList(new LambdaQueryWrapper<ToolLog>()
                    .orderByDesc(ToolLog::getId)
                    .last("LIMIT 50"));
            return new ArrayList<>(rows);
        } catch (Exception e) {
            log.warn("read tool logs from db failed, fallback in-memory: {}", e.getMessage());
            return new ArrayList<>(callLog);
        }
    }

    private void persistLog(String sessionId, String name, Map<String, Object> args, Map<String, Object> result,
                            boolean success, long duration) {
        try {
            ToolLog toolLog = new ToolLog();
            toolLog.setSessionId(sessionId);
            toolLog.setTool(name);
            toolLog.setArgumentsJson(objectMapper.writeValueAsString(redactArgs(args)));
            String resultJson = objectMapper.writeValueAsString(redactArgs(result));
            if (resultJson.length() > 4000) {
                resultJson = resultJson.substring(0, 4000) + "...[truncated]";
            }
            toolLog.setResultJson(resultJson);
            toolLog.setSuccess(success);
            toolLog.setDurationMs(duration);
            toolLog.setCreatedAt(LocalDateTime.now());
            toolLogMapper.insert(toolLog);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("persist tool log failed: {}", e.getMessage());
        }
    }

    private Map<String, Object> redactArgs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>(args);
        Object content = copy.get("content");
        if (content != null) {
            String text = content.toString();
            if (text.length() > 160) {
                copy.put("content", "[redacted " + text.length() + " chars]");
            }
        }
        copy.remove("password");
        copy.remove("confirmToken");
        return copy;
    }

    private Map<String, Object> dispatch(String name, Map<String, Object> args) {
        return switch (name) {
            case "ticket_create" -> {
                Ticket ticket = ticketService.create(
                        str(args, "session_id"),
                        str(args, "user_label"),
                        str(args, "title"),
                        str(args, "description"),
                        str(args, "priority")
                );
                yield Map.of(
                        "success", true,
                        "ticketNo", ticket.getTicketNo(),
                        "status", ticket.getStatus()
                );
            }
            case "ticket_query" -> {
                Ticket ticket = ticketService.list().stream()
                        .filter(t -> t.getTicketNo().equalsIgnoreCase(str(args, "ticket_no")))
                        .findFirst()
                        .orElse(null);
                yield ticket == null
                        ? Map.of("success", false, "error", "未找到待办")
                        : Map.of("success", true, "ticketNo", ticket.getTicketNo(),
                        "status", ticket.getStatus(),
                        "statusLabel", TicketStatus.zhOf(ticket.getStatus()),
                        "title", ticket.getTitle());
            }
            case "ticket_list" -> ticketList();
            case "ticket_update" -> {
                Ticket before = ticketService.findByNo(str(args, "ticket_no"));
                if (before == null) {
                    yield Map.of("success", false, "error", "未找到待办 " + str(args, "ticket_no"));
                }
                String fromLabel = TicketStatus.zhOf(before.getStatus());
                Ticket ticket = ticketService.updateStatusByNo(str(args, "ticket_no"), str(args, "status"));
                yield Map.of(
                        "success", true,
                        "updated", true,
                        "ticketNo", ticket.getTicketNo(),
                        "title", ticket.getTitle(),
                        "fromLabel", fromLabel,
                        "status", ticket.getStatus(),
                        "statusLabel", TicketStatus.zhOf(ticket.getStatus())
                );
            }
            case "memo_save" -> {
                String title = str(args, "title");
                String content = str(args, "content");
                var doc = knowledgeService.ingest(title, "memo", content);
                yield Map.of("success", true, "title", doc.getTitle(), "documentId", doc.getId(),
                        "chunkCount", doc.getChunkCount() == null ? 0 : doc.getChunkCount());
            }
            case "kb_search" -> {
                List<RagHit> hits = knowledgeService.search(str(args, "query"));
                yield Map.of("success", true, "hits", hits);
            }
            case "blog_search" -> Map.of("success", true, "articles", blogClient.search(str(args, "query")));
            case "blog_list" -> blogList(args);
            case "blog_bookmarks" -> blogBookmarkList();
            case "blog_get" -> Map.of("success", true, "article", blogClient.getArticle(str(args, "slug")));
            case "blog_sync_slug" -> blogSyncSlug(args);
            case "blog_article_upsert" -> blogArticle(args);
            case "blog_bookmark_create" -> blogBookmark(args);
            case "blog_tag_create" -> blogTag(args);
            case "stock_quote" -> stockInsightService.lookup(firstNonBlank(str(args, "query"), str(args, "symbol")));
            case "order_query" -> {
                String orderId = str(args, "order_id");
                yield Map.of(
                        "success", true,
                        "orderId", orderId,
                        "status", "shipped",
                        "product", "理财产品A（演示数据）",
                        "amount", 299.00
                );
            }
            case "tea_order" -> teaOrder(args);
            default -> Map.of("success", false, "error", "unknown tool: " + name);
        };
    }

    private Map<String, Object> ticketList() {
        List<Ticket> all = ticketService.list();
        List<Map<String, Object>> items = new ArrayList<>();
        int limit = Math.min(20, all.size());
        for (int i = 0; i < limit; i++) {
            Ticket ticket = all.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ticketNo", ticket.getTicketNo());
            row.put("title", ticket.getTitle());
            row.put("status", ticket.getStatus());
            row.put("statusLabel", TicketStatus.zhOf(ticket.getStatus()));
            items.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("count", all.size());
        result.put("items", items);
        return result;
    }

    private Map<String, Object> blogList(Map<String, Object> args) {
        if (!blogClient.enabled()) {
            return Map.of("success", false, "error", "未配置 BLOG_BASE_URL");
        }
        List<Map<String, Object>> articles = blogClient.listArticles(str(args, "query"), 1, 30);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> article : articles) {
            String slug = str(article, "slug");
            if (slug.isBlank()) {
                slug = str(article, "id");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slug", slug);
            row.put("title", str(article, "title"));
            row.put("summary", str(article, "summary"));
            row.put("category", firstNonBlank(str(article, "categoryName"), str(article, "category")));
            row.put("publishTime", article.get("publishTime"));
            row.put("url", blogClient.articleUrl(slug));
            row.put("ingested", knowledgeService.findByBlogSlug(slug) != null);
            items.add(row);
        }
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("kind", "blog_list");
        embed.put("articles", items);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("count", items.size());
        result.put("embed", embed);
        return result;
    }

    private Map<String, Object> blogBookmarkList() {
        if (!blogClient.enabled()) {
            return Map.of("success", false, "error", "未配置 BLOG_BASE_URL");
        }
        List<Map<String, Object>> groups = blogClient.listBookmarkGroups();
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("kind", "bookmark_list");
        embed.put("groups", groups);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("count", groups.size());
        result.put("embed", embed);
        return result;
    }

    private Map<String, Object> blogSyncSlug(Map<String, Object> args) {
        try {
            return blogSyncService.syncSlug(str(args, "slug"));
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage() == null ? "同步失败" : e.getMessage());
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a == null || a.isBlank() ? (b == null ? "" : b) : a;
    }
        return Map.of("success", false, "error", blogAdminClient.writeBlockedReason());
    }

    private Map<String, Object> blogArticle(Map<String, Object> args) {
        if (!blogAdminClient.writeReady()) {
            return blockedWrite();
        }
        boolean publish = str(args, "action").contains("发布");
        Map<String, Object> result = blogAdminClient.createArticle(
                str(args, "title"),
                str(args, "summary"),
                str(args, "content"),
                str(args, "category"),
                str(args, "tags"),
                publish
        );
        if (publish && result.get("slug") != null) {
            try {
                knowledgeService.upsertBlog(
                        str(args, "title"),
                        String.valueOf(result.get("slug")),
                        str(args, "content")
                );
                result.put("ingested", true);
            } catch (Exception e) {
                log.warn("blog ingest after publish failed: {}", e.getMessage());
                result.put("ingested", false);
            }
        }
        return result;
    }

    private Map<String, Object> blogBookmark(Map<String, Object> args) {
        if (!blogAdminClient.writeReady()) {
            return blockedWrite();
        }
        return blogAdminClient.createBookmark(
                str(args, "name"),
                str(args, "link"),
                str(args, "description"),
                str(args, "category")
        );
    }

    private Map<String, Object> blogTag(Map<String, Object> args) {
        if (!blogAdminClient.writeReady()) {
            return blockedWrite();
        }
        return blogAdminClient.createTag(str(args, "name"));
    }

    private Map<String, Object> teaOrder(Map<String, Object> args) {
        String drink = str(args, "drink");
        String size = str(args, "size");
        String topping = str(args, "topping");
        int count = parseCount(str(args, "count"));
        int unit = switch (drink) {
            case "伯牙绝弦" -> 22;
            case "多肉葡萄" -> 19;
            case "生椰拿铁" -> 18;
            default -> 12;
        };
        if ("大杯".equals(size)) {
            unit += 2;
        }
        if ("珍珠".equals(topping)) {
            unit += 2;
        } else if ("椰果".equals(topping)) {
            unit += 1;
        }
        int total = unit * count;
        String orderNo = "TEA-" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("orderNo", orderNo);
        result.put("drink", drink);
        result.put("size", size);
        result.put("sweetness", str(args, "sweetness"));
        result.put("ice", str(args, "ice"));
        result.put("topping", topping);
        result.put("count", count);
        result.put("desk", str(args, "desk"));
        result.put("etaMinutes", 12);
        result.put("total", total);
        result.put("status", "making");
        return result;
    }

    private int parseCount(String raw) {
        try {
            int n = Integer.parseInt(raw.replaceAll("[^0-9]", ""));
            return n <= 0 ? 1 : Math.min(n, 9);
        } catch (Exception e) {
            return 1;
        }
    }

    private Map<String, Object> tool(String name, String description, List<String> params) {
        return Map.of("name", name, "description", description, "params", params);
    }

    private String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? "" : v.toString();
    }
}
