package com.lumencs.modules.workflow;

import com.lumencs.agent.AgentEventSink;
import com.lumencs.agent.AgentState;
import com.lumencs.memory.LongTermMemoryService;
import com.lumencs.memory.WorkingMemoryService;
import com.lumencs.modules.blogwrite.BlogDraftComposer;
import com.lumencs.modules.mcp.McpToolServer;
import com.lumencs.model.entity.TicketStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class WorkflowAgent {

    private final WorkingMemoryService workingMemory;
    private final LongTermMemoryService longTermMemory;
    private final McpToolServer mcpToolServer;
    private final BlogDraftComposer blogDraftComposer;

    public WorkflowAgent(
            WorkingMemoryService workingMemory,
            LongTermMemoryService longTermMemory,
            McpToolServer mcpToolServer,
            BlogDraftComposer blogDraftComposer) {
        this.workingMemory = workingMemory;
        this.longTermMemory = longTermMemory;
        this.mcpToolServer = mcpToolServer;
        this.blogDraftComposer = blogDraftComposer;
    }

    public AgentState process(AgentState state, AgentEventSink sink) {
        WorkflowDef def = WorkflowCatalog.of(state.getIntent());
        if (def == null) {
            return state;
        }
        workingMemory.put(state.getSessionId(), "workflow", def.id());
        workingMemory.put(state.getSessionId(), "intent", state.getIntent());
        workingMemory.mergeSlots(state.getSessionId(), WorkflowCatalog.extractSlots(def.id(), state.getUserMessage()));

        Map<String, Object> slots = workingMemory.getMap(state.getSessionId(), "slots");
        Map<String, Object> profile = longTermMemory.profile(state.getUserLabel());
        boolean prefilled = false;
        for (WorkflowSlot slot : def.slots()) {
            if (blank(slots.get(slot.name())) && !blank(profile.get(slot.name()))) {
                slots.put(slot.name(), profile.get(slot.name()));
                prefilled = true;
            }
        }
        if (prefilled) {
            workingMemory.put(state.getSessionId(), "slots", slots);
        }

        if (!state.isCardSubmit() && "blog_article".equals(def.id()) && blank(slots.get("content"))) {
            sink.step("workflow", "draft", Map.of("workflow", def.id()));
            BlogDraftComposer.ArticleDraft draft = blogDraftComposer.compose(state);
            fillIfBlank(slots, "title", draft.title());
            fillIfBlank(slots, "summary", draft.summary());
            fillIfBlank(slots, "content", draft.content());
            fillIfBlank(slots, "tags", draft.tags());
            fillIfBlank(slots, "category", draft.category());
            fillIfBlank(slots, "action", draft.action());
            workingMemory.put(state.getSessionId(), "slots", slots);
            prefilled = true;
        }

        if (!state.isCardSubmit() && WorkflowCatalog.isDirectQuery(def.id())) {
            return runDirect(state, sink, def, slots);
        }

        if (!state.isCardSubmit()) {
            emitCard(state, sink, def, slots, prefilled);
            return state;
        }

        List<String> missing = WorkflowCatalog.missing(def, slots);
        sink.step("workflow", "check_slots", Map.of("workflow", def.id(), "missing", missing));
        if (!missing.isEmpty()) {
            emitCard(state, sink, def, slots, prefilled);
            return state;
        }

        String tool = def.tool();
        if ("todo_query".equals(def.id())) {
            tool = blank(slots.get("ticketNo")) ? "ticket_list" : "ticket_query";
        }
        if ("blog_sync".equals(def.id()) && blank(slots.get("slug"))) {
            tool = "blog_list";
        }
        Map<String, Object> args = new LinkedHashMap<>(slots);
        args.put("session_id", state.getSessionId());
        args.put("user_label", state.getUserLabel());
        if ("ticket_create".equals(tool)) {
            args.put("title", String.valueOf(slots.getOrDefault("title", def.title())));
            args.put("description", buildDescription(def, slots));
            args.put("priority", "MEDIUM");
        }
        if (slots.containsKey("ticketNo")) {
            args.put("ticket_no", slots.get("ticketNo"));
        }
        if ("ticket_update".equals(tool) && slots.containsKey("status")) {
            args.put("status", slots.get("status"));
        }
        sink.step("workflow", "call_tool", Map.of("tool", tool));
        Map<String, Object> toolResult = mcpToolServer.call(state.getSessionId(), tool, args);
        attachEmbed(state, sink, toolResult);
        state.getSubResults().put("workflow", formatResult(def, toolResult));
        if (toolResult.get("ticketNo") != null) {
            state.setTicketNo(String.valueOf(toolResult.get("ticketNo")));
        }
        if ("milk_tea".equals(def.id()) && !Boolean.FALSE.equals(toolResult.get("success"))) {
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("drink", slots.getOrDefault("drink", ""));
            facts.put("size", slots.getOrDefault("size", ""));
            facts.put("sweetness", slots.getOrDefault("sweetness", ""));
            facts.put("ice", slots.getOrDefault("ice", ""));
            facts.put("topping", slots.getOrDefault("topping", ""));
            facts.put("count", slots.getOrDefault("count", "1"));
            facts.put("desk", slots.getOrDefault("desk", ""));
            longTermMemory.remember(state.getUserLabel(), facts);
        }
        workingMemory.clearWorkflow(state.getSessionId());
        sink.step("workflow", "done", toolResult);
        return state;
    }

    private AgentState runDirect(AgentState state, AgentEventSink sink, WorkflowDef def, Map<String, Object> slots) {
        String tool = def.tool();
        Map<String, Object> args = new LinkedHashMap<>();
        if ("todo_query".equals(def.id())) {
            tool = blank(slots.get("ticketNo")) ? "ticket_list" : "ticket_query";
            if (!blank(slots.get("ticketNo"))) {
                args.put("ticket_no", slots.get("ticketNo"));
            }
        } else if ("blog_sync".equals(def.id()) && blank(slots.get("slug"))) {
            tool = "blog_list";
        } else {
            args.putAll(slots);
        }
        sink.step("workflow", "call_tool", Map.of("tool", tool));
        Map<String, Object> toolResult = mcpToolServer.call(state.getSessionId(), tool, args);
        attachEmbed(state, sink, toolResult);
        state.getSubResults().put("workflow", formatResult(def, toolResult));
        workingMemory.clearWorkflow(state.getSessionId());
        sink.step("workflow", "done", toolResult);
        return state;
    }

    @SuppressWarnings("unchecked")
    private void attachEmbed(AgentState state, AgentEventSink sink, Map<String, Object> toolResult) {
        Object raw = toolResult.get("embed");
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> embed = new LinkedHashMap<>();
            map.forEach((k, v) -> embed.put(String.valueOf(k), v));
            state.setEmbed(embed);
            sink.embed(embed);
        }
    }

    private void fillIfBlank(Map<String, Object> slots, String key, String value) {
        if (blank(slots.get(key)) && value != null && !value.isBlank()) {
            slots.put(key, value);
        }
    }

    private void emitCard(AgentState state, AgentEventSink sink, WorkflowDef def, Map<String, Object> slots, boolean prefilled) {
        String cardId = UUID.randomUUID().toString();
        String confirmToken = workingMemory.issueConfirm(state.getSessionId(), cardId, def.id());
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardId", cardId);
        card.put("confirmToken", confirmToken);
        card.put("workflow", def.id());
        card.put("title", def.title());
        String hint = def.hint();
        if ("blog_article".equals(def.id()) && prefilled) {
            hint = "已根据对话生成草稿，请改标题和正文后确认。默认存草稿。";
        } else if ("milk_tea".equals(def.id()) && prefilled) {
            hint = "已按你上次的口味预填，改一下再确认即可。";
        }
        card.put("hint", hint);
        card.put("prefilled", prefilled);
        card.put("fields", def.slots().stream().map(slot -> {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", slot.name());
            field.put("type", slot.type());
            field.put("label", slot.label());
            field.put("required", slot.required());
            field.put("options", slot.options());
            Object value = slots.getOrDefault(slot.name(), "");
            field.put("value", value == null || "null".equals(String.valueOf(value)) ? "" : String.valueOf(value));
            return field;
        }).toList());
        sink.card(card);
        state.setWaitingCard(true);
        state.getSubResults().put("workflow", "blog_article".equals(def.id()) && prefilled
                ? "已生成博客草稿，请在卡片里修改后确认提交。"
                : def.hint());
        sink.step("workflow", "await_card", Map.of("workflow", def.id(), "prefilled", prefilled));
    }

    private boolean blank(Object value) {
        return value == null || value.toString().isBlank() || "null".equals(value.toString());
    }

    private static String text(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String buildDescription(WorkflowDef def, Map<String, Object> slots) {
        StringBuilder sb = new StringBuilder(def.title()).append('\n');
        slots.forEach((k, v) -> sb.append(k).append(": ").append(v).append('\n'));
        return sb.toString();
    }

    private String formatResult(WorkflowDef def, Map<String, Object> result) {
        if (Boolean.FALSE.equals(result.get("success"))) {
            return def.title() + " 未能完成：" + result.getOrDefault("error", "未知错误");
        }
        if (Boolean.TRUE.equals(result.get("updated"))) {
            return "已将 " + result.get("ticketNo") + "「" + result.getOrDefault("title", "")
                    + "」从 " + result.getOrDefault("fromLabel", "")
                    + " 改为 " + result.getOrDefault("statusLabel", result.get("status")) + "。";
        }
        if ("ticket_create".equals(def.tool()) && result.get("ticketNo") != null) {
            return "待办已记下。编号：" + result.get("ticketNo")
                    + "，当前状态：" + TicketStatus.zhOf(String.valueOf(result.getOrDefault("status", "CREATED")));
        }
        if (result.get("items") instanceof List<?> items) {
            if (items.isEmpty()) {
                return "目前没有待办。可以说「加个待办：…」记一条。";
            }
            int total = result.get("count") instanceof Number n ? n.intValue() : items.size();
            StringBuilder sb = new StringBuilder();
            sb.append("你现在有 ").append(total).append(" 条待办");
            if (total > items.size()) {
                sb.append("（先列出最近 ").append(items.size()).append(" 条）");
            }
            sb.append("：\n");
            int i = 1;
            for (Object row : items) {
                if (row instanceof Map<?, ?> map) {
                    String label = text(map, "statusLabel");
                    if (label.isBlank()) {
                        label = text(map, "status");
                    }
                    sb.append(i++).append(". ")
                            .append(text(map, "ticketNo"))
                            .append("  ")
                            .append(text(map, "title"))
                            .append("  ·  ")
                            .append(label)
                            .append('\n');
                }
            }
            sb.append("改状态可以说「把 TK-… 改成进行中」。要看某一条，把编号发我即可。");
            return sb.toString().strip();
        }
        if (result.get("ticketNo") != null) {
            return "待办 " + result.get("ticketNo") + "「" + result.getOrDefault("title", "")
                    + "」当前状态：" + result.getOrDefault("statusLabel", result.getOrDefault("status", ""));
        }
        if (result.get("orderNo") != null) {
            return """
                    下单成功，骑手正向工位赶。
                    单号：%s
                    %s %s · %s · %s · %s × %s
                    送到：%s
                    预计 %s 分钟，合计 ¥%s
                    （演示订单，未对接真实外卖）
                    """.formatted(
                    result.get("orderNo"),
                    result.getOrDefault("size", ""),
                    result.getOrDefault("drink", ""),
                    result.getOrDefault("sweetness", ""),
                    result.getOrDefault("ice", ""),
                    result.getOrDefault("topping", ""),
                    result.getOrDefault("count", 1),
                    result.getOrDefault("desk", ""),
                    result.getOrDefault("etaMinutes", 12),
                    result.getOrDefault("total", 0)
            ).strip();
        }
        if ("memo_save".equals(def.tool())) {
            return "已写入知识库「" + result.getOrDefault("title", "") + "」。之后可以直接问我。";
        }
        if ("blog_article_upsert".equals(def.tool())) {
            boolean published = Boolean.TRUE.equals(result.get("published"));
            String url = String.valueOf(result.getOrDefault("publicUrl", ""));
            if (published && !url.isBlank() && !"null".equals(url)) {
                return "已发布到博客前台。\n标题：" + result.getOrDefault("title", "")
                        + "\n链接：" + url;
            }
            return "已写入博客草稿（前台不可见）。\n标题：" + result.getOrDefault("title", "")
                    + "\n可到管理后台继续编辑发布。";
        }
        if ("blog_bookmark_create".equals(def.tool())) {
            return "已添加书签「" + result.getOrDefault("name", "") + "」→ "
                    + result.getOrDefault("link", "");
        }
        if ("blog_tag_create".equals(def.tool())) {
            return Boolean.FALSE.equals(result.get("created"))
                    ? "标签已存在，已复用「" + result.getOrDefault("name", "") + "」。"
                    : "已新建文章标签「" + result.getOrDefault("name", "") + "」。";
        }
        if ("blog_list".equals(def.id()) || "blog_list".equals(def.tool())
                || ("blog_sync".equals(def.id()) && result.get("embed") instanceof Map<?, ?>)) {
            int count = result.get("count") instanceof Number n ? n.intValue() : 0;
            if (count == 0) {
                return "前台暂时没有已发布文章，或还没配 BLOG_BASE_URL。";
            }
            return "下面是已发布的 " + count + " 篇。点「同步」写入本仓知识库；点「聊这篇」只问这一篇。";
        }
        if ("blog_bookmarks".equals(def.id())) {
            if (Boolean.FALSE.equals(result.get("success"))) {
                return def.title() + " 未能完成：" + result.getOrDefault("error", "未知错误");
            }
            return "下面是博客书签。点链接会新开前台。";
        }
        if ("blog_sync_slug".equals(def.tool()) && result.get("slug") != null) {
            return (Boolean.TRUE.equals(result.get("created")) ? "已同步进知识库：" : "已更新知识库：")
                    + result.getOrDefault("title", result.get("slug"))
                    + "。之后可以直接问这篇，或打开「聊这篇」。";
        }
        if ("stock_quote".equals(def.id())) {
            if (Boolean.FALSE.equals(result.get("success"))) {
                return String.valueOf(result.getOrDefault("error", "行情查询失败"));
            }
            Object pct = result.get("changePct");
            String change = pct == null || "null".equals(String.valueOf(pct)) ? "" : "（" + pct + "%）";
            return result.getOrDefault("name", "") + " " + result.getOrDefault("symbol", "")
                    + " 现价 " + result.getOrDefault("price", "—") + change
                    + " · " + result.getOrDefault("actionLabel", "")
                    + "。行情来自盯盘侠，仅供参考。";
        }
        return def.title() + " 查询结果：" + result;
    }
}
