package com.lumencs.agent;

import com.lumencs.memory.WorkingMemoryService;
import com.lumencs.modules.workflow.WorkflowCatalog;
import com.lumencs.tracing.AgentTracer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 意图路由：关键词优先（确定性，置信度高）；未命中再调 LLM，
 * LLM 返回 JSON {intent, confidence}。置信度低于阈值时标记需要澄清，
 * Supervisor 不再派发业务 Agent，改为向用户确认意图。
 */
@Component
public class IntentRouterAgent {

    private static final Set<String> INTENTS = Set.of(
            "knowledge_rag", "memo", "todo", "todo_query", "todo_update",
            "compliance_checker", "milk_tea", "chitchat",
            "blog_article", "blog_bookmark", "blog_tag", "blog_list", "blog_bookmarks", "blog_sync",
            "stock_quote"
    );

    /** 低于该置信度触发澄清（LLM 兜底默认 0.6，仅 LLM 明确低置信才澄清） */
    private static final double CLARIFY_THRESHOLD = 0.55;
    /** 关键词确定性命中（规则而非模型判断）的置信度 */
    private static final double RULE_CONFIDENCE = 0.95;

    private static final String SYSTEM_PROMPT = """
            你是意图识别Agent。只返回 JSON，格式：{"intent": "...", "confidence": 0.0-1.0}
            intent 只能是以下之一：
            knowledge_rag, memo, todo, todo_query, todo_update, compliance_checker, milk_tea, chitchat, blog_article, blog_bookmark, blog_tag, blog_list, blog_bookmarks, blog_sync, stock_quote
            规则：
            - 问自己的文档、笔记、博客正文、怎么做、是什么 → knowledge_rag
            - 帮我记一下、备忘、写进知识库 → memo
            - 加待办、提醒我、别忘了 → todo
            - 有哪些待办、待办列表、查待办、事项进度 → todo_query
            - 修改待办、改成进行中/已完成、把 TK- 改状态 → todo_update
            - 盗刷、欺诈、举报 → compliance_checker
            - 点奶茶、点咖啡、下午茶、口渴、加班喝一杯、再来一杯 → milk_tea
            - 列出已发布博客、博客列表、我发过哪些文章 → blog_list
            - 书签列表、我的书签、列出收藏 → blog_bookmarks
            - 同步这篇博客、把某篇文章同步到知识库 → blog_sync
            - 写博客、发文章、存草稿、帮我写成博文、发布到博客 → blog_article
            - 收藏链接、加书签、收藏这个网址（新建，不是列表） → blog_bookmark
            - 新建标签、创建一个文章标签（不是给书签打标签） → blog_tag
            - 查某只股票、行情、K线、现价、盯盘侠 → stock_quote
            - 问候、闲聊、你是谁、日常问题、心情天气 → chitchat
            confidence：表述明确时接近 1.0。
            闲聊请给 0.7 以上，不要把「你好」标成低置信去澄清。
            只有完全不知道用户要干什么（例如「帮我弄一下」）才把 confidence 压到 0.5 以下。
            """;

    private static final String CLARIFICATION_TEXT = """
            我还没太理解。您是想：
            1. 问知识库里的笔记 / 文档
            2. 记一笔到知识库
            3. 加一条待办
            4. 看待办列表 / 查进度
            5. 改待办状态
            6. 写博客 / 存草稿 / 看已发布列表 / 同步一篇
            7. 查一只股票行情
            8. 点一杯奶茶（演示）
            9. 随便聊聊
            请再说具体一点。""";

    private final ChatClient chatClient;
    private final AgentTracer tracer;
    private final WorkingMemoryService workingMemory;

    public IntentRouterAgent(ChatClient chatClient, AgentTracer tracer, WorkingMemoryService workingMemory) {
        this.chatClient = chatClient;
        this.tracer = tracer;
        this.workingMemory = workingMemory;
    }

    public AgentState process(AgentState state, AgentEventSink sink) {
        sink.step("intent_router", "start", Map.of());
        IntentResult result = tracer.trace(state.getSessionId(), "intent_router", "process", Map.of(),
                () -> classify(state));
        state.setIntent(result.intent());
        state.setIntentConfidence(result.confidence());
        if (result.confidence() < CLARIFY_THRESHOLD) {
            state.setNeedsClarification(true);
            state.setClarification(CLARIFICATION_TEXT);
        }
        sink.step("intent_router", "done", Map.of(
                "intent", result.intent(),
                "confidence", result.confidence(),
                "clarify", state.isNeedsClarification(),
                "workflow", WorkflowNames.workflow(result.intent())
        ));
        return state;
    }

    private IntentResult classify(AgentState state) {
        String message = state.getUserMessage() == null ? "" : state.getUserMessage();
        String last = workingMemory.getString(state.getSessionId(), "intent");
        String pending = workingMemory.getString(state.getSessionId(), "pendingCardId");
        String keyed = keywordFallback(message);

        // 办事流程续接：卡片未提交时短句不打断流程
        if (!pending.isBlank() && WorkflowCatalog.isWorkflow(last)) {
            if (isStrongSwitch(keyed, last, message)) {
                workingMemory.clearWorkflow(state.getSessionId());
            } else {
                return new IntentResult(last, RULE_CONFIDENCE);
            }
        }
        // 追问「再来一杯」延续上次流程
        if (isFollowUp(message) && WorkflowCatalog.isWorkflow(last)) {
            return new IntentResult(last, RULE_CONFIDENCE);
        }
        // 关键词确定性命中（非默认 knowledge_rag）
        if (!"knowledge_rag".equals(keyed)) {
            return new IntentResult(keyed, RULE_CONFIDENCE);
        }
        // 关键词未命中 → LLM 结构化输出（intent + confidence）
        try {
            IntentResult result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(message)
                    .call()
                    .entity(IntentResult.class);
            if (result != null && result.intent() != null && INTENTS.contains(result.intent().trim().toLowerCase())) {
                return new IntentResult(result.intent().trim().toLowerCase(), clamp(result.confidence()));
            }
        } catch (Exception ignored) {
            // 关键词已兜底
        }
        return new IntentResult(keyed, 0.6);
    }

    private boolean isStrongSwitch(String keyed, String last, String message) {
        if (!keyed.equals(last) && WorkflowCatalog.isWorkflow(keyed)) {
            return true;
        }
        if ("knowledge_rag".equals(keyed)
                && !WorkflowCatalog.mentionsSlotOption(last, message)
                && containsAny(message, "多少", "怎么", "什么", "文档", "笔记", "知识库")) {
            return true;
        }
        return false;
    }

    private boolean isFollowUp(String message) {
        return containsAny(message, "再来", "老样子", "同样", "再点", "还是那", "老工位", "一样的");
    }

    private String keywordFallback(String message) {
        String msg = message == null ? "" : message;
        if (containsAny(msg, "记一下", "记下", "备忘", "记一笔", "写进知识库", "存到知识库")) {
            return "memo";
        }
        if (!containsAny(msg, "加个待办", "记个待办", "新增待办")) {
            if (containsAny(msg, "修改待办", "修改代办", "改状态", "这条待办", "这条代办")) {
                return "todo_update";
            }
            if (msg.contains("TK-") && containsAny(msg, "改成", "改为", "标记为",
                    "进行中", "已完成", "已关闭", "已升级", "等待处理", "已创建")) {
                return "todo_update";
            }
        }
        if (containsAny(msg, "待办号", "查待办", "事项进度", "待办编号", "哪些待办", "哪些代办",
                "待办列表", "代办列表", "有什么待办", "有哪些代办")) {
            return "todo_query";
        }
        if (containsAny(msg, "待办", "代办") && containsAny(msg, "哪些", "列表", "全部", "有什么", "都有", "查")) {
            return "todo_query";
        }
        if (containsAny(msg, "待办", "提醒我", "别忘了", "记个待办")) {
            return "todo";
        }
        if (containsAny(msg, "奶茶", "咖啡", "点单", "下午茶", "口渴", "生椰", "伯牙", "再来一杯")) {
            return "milk_tea";
        }
        if (containsAny(msg, "同步这篇博客", "同步博客", "同步这篇文章", "同步到知识库")
                || (msg.contains("同步") && containsAny(msg, "这篇", "该篇") && containsAny(msg, "博客", "文章"))) {
            return "blog_sync";
        }
        if (containsAny(msg, "博客列表", "已发布的博客", "已发布博客", "文章列表", "列出博客", "我发过的", "博客有哪些")) {
            return "blog_list";
        }
        if (containsAny(msg, "书签列表", "我的书签", "列出书签", "收藏夹", "书签有哪些")) {
            return "blog_bookmarks";
        }
        if (containsAny(msg, "写博客", "发文章", "发一篇", "写一篇", "存草稿", "发布文章", "写成博文", "博客草稿")) {
            return "blog_article";
        }
        if (containsAny(msg, "加书签", "收藏这个", "收藏链接", "添加书签", "收藏网址")) {
            return "blog_bookmark";
        }
        if (containsAny(msg, "新建标签", "创建标签", "加个标签")) {
            return "blog_tag";
        }
        if (containsAny(msg, "行情", "K线", "k线", "看盘", "盯盘侠", "现价", "股价")
                || (msg.contains("股票") && containsAny(msg, "查", "看", "多少", "怎么", "怎样"))) {
            return "stock_quote";
        }
        if (msg.matches("(?s).*\\d{6}.*") && containsAny(msg, "股票", "查", "看", "行情", "多少")) {
            return "stock_quote";
        }
        if (containsAny(msg, "举报", "欺诈", "盗刷", "泄露")) {
            return "compliance_checker";
        }
        if (isCasualTalk(msg)) {
            return "chitchat";
        }
        return "knowledge_rag";
    }

    /** 短问候或「你是谁」走闲聊；「你好，收益多少」这种长句仍交给后面的 LLM。 */
    private boolean isCasualTalk(String msg) {
        String t = msg == null ? "" : msg.trim();
        if (t.isEmpty()) {
            return false;
        }
        if (containsAny(t, "你是谁", "你叫什么", "介绍一下你", "你会什么", "你能做什么")) {
            return true;
        }
        boolean greeting = containsAny(t, "你好", "您好", "嗨", "在吗", "早上好", "晚上好", "中午好",
                "谢谢", "再见", "拜拜", "hello", "hi", "hey");
        return greeting && t.length() <= 16;
    }

    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** LLM 结构化输出：意图 + 置信度 */
    public record IntentResult(String intent, double confidence) {
    }

    private static final class WorkflowNames {
        static String workflow(String intent) {
            return WorkflowCatalog.isWorkflow(intent) ? intent : "none";
        }
    }
}
