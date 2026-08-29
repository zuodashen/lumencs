package com.lumencs.agent;

import com.lumencs.service.KnowledgeService;
import com.lumencs.memory.ShortTermMemoryService;
import com.lumencs.modules.mcp.BlogClient;
import com.lumencs.rag.RagHit;
import com.lumencs.tracing.AgentTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 知识 RAG Agent：Query 改写（LLM，可配置关闭）→ 向量检索 TopK（sidecar）
 * → LLM 重排 Top3（可配置关闭，失败按分数序）→ 注入上下文生成 + 引用（可点击）。
 */
@Component
public class KnowledgeRAGAgent {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRAGAgent.class);

    private static final String RAG_PROMPT = """
            你是个人知识库管家。严格基于检索到的笔记/文档回答，不要编造。
            没有相关内容时明确说知识库里没有，并建议把资料上传到控制台知识库，或说「帮我记一下」。
            在回答末尾用「引用来源：」列出用到的来源名。

            最近对话：
            %s

            当前会话上下文（工作记忆 / 用户画像）：
            %s

            检索到的文档：
            %s

            用户问题：%s
            """;

    private static final String REWRITE_PROMPT = """
            你是检索查询改写助手。把用户的追问或口语化表达改写成适合向量检索的独立完整问题。
            只输出改写后的查询文本本身，不要任何解释、引号或多余内容。
            """;

    private static final String RERANK_PROMPT = """
            你是文档重排器。根据查询与候选文档的相关性，选出最相关的若干文档 id，
            按相关性从高到低排序。只能从给定的候选 id 中选择，不要新增 id。
            只输出 JSON：{"ids": ["id1", "id2", ...]}
            """;

    private final ChatClient chatClient;
    private final KnowledgeService knowledgeService;
    private final ShortTermMemoryService shortTermMemory;
    private final BlogClient blogClient;
    private final AgentTracer tracer;
    private final boolean rewriteEnabled;
    private final boolean rerankEnabled;
    private final int rerankTop;

    public KnowledgeRAGAgent(
            ChatClient chatClient,
            KnowledgeService knowledgeService,
            ShortTermMemoryService shortTermMemory,
            BlogClient blogClient,
            AgentTracer tracer,
            @Value("${lumencs.rag.rewrite-enabled}") boolean rewriteEnabled,
            @Value("${lumencs.rag.rerank-enabled}") boolean rerankEnabled,
            @Value("${lumencs.rag.rerank-top}") int rerankTop) {
        this.chatClient = chatClient;
        this.knowledgeService = knowledgeService;
        this.shortTermMemory = shortTermMemory;
        this.blogClient = blogClient;
        this.tracer = tracer;
        this.rewriteEnabled = rewriteEnabled;
        this.rerankEnabled = rerankEnabled;
        this.rerankTop = rerankTop;
    }

    public AgentState process(AgentState state, AgentEventSink sink) {
        sink.step("knowledge_rag", "start", Map.of());
        Long scopedDocId = null;
        if (state.getArticleSlug() != null && !state.getArticleSlug().isBlank()) {
            var doc = knowledgeService.findByBlogSlug(state.getArticleSlug());
            if (doc != null) {
                scopedDocId = doc.getId();
                sink.step("knowledge_rag", "scope", Map.of(
                        "slug", state.getArticleSlug(),
                        "documentId", scopedDocId,
                        "title", doc.getTitle()
                ));
            }
        }
        String searchQuery = rewriteQuery(state);
        Long docId = scopedDocId;
        List<RagHit> hits = tracer.trace(state.getSessionId(), "knowledge_rag", "search", Map.of("query", searchQuery),
                () -> knowledgeService.search(searchQuery, docId));

        List<RagHit> ranked = rerank(hits, searchQuery);
        sink.step("knowledge_rag", "retrieved", Map.of(
                "query", searchQuery,
                "hitCount", hits.size(),
                "rankedCount", ranked.size()
        ));

        List<Map<String, Object>> citations = new ArrayList<>();
        for (RagHit hit : ranked) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", hit.getId());
            item.put("documentId", hit.getDocumentId());
            item.put("source", hit.getSource());
            item.put("score", hit.getScore());
            item.put("snippet", hit.getContent() == null ? "" : trim(hit.getContent(), 180));
            citations.add(item);
        }
        List<Map<String, Object>> blogArticles = (scopedDocId == null && blogClient.enabled())
                ? blogClient.search(state.getUserMessage())
                : List.of();
        for (Map<String, Object> article : blogArticles) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", String.valueOf(article.getOrDefault("id", "blog")));
            item.put("documentId", null);
            item.put("source", "博客 · " + article.getOrDefault("title", "文章"));
            item.put("score", 0.5);
            item.put("snippet", String.valueOf(article.getOrDefault("summary", article.getOrDefault("title", ""))));
            citations.add(item);
        }
        state.setCitations(citations);
        sink.step("knowledge_rag", "citations", Map.of("count", citations.size(), "citations", citations));

        String context = ranked.stream()
                .map(hit -> "来源: " + hit.getSource() + "\n内容: " + hit.getContent())
                .collect(Collectors.joining("\n---\n"));
        if (!blogArticles.isEmpty()) {
            context += "\n---\n博客检索（只读公开 API）：" + blogArticles;
        }
        if (context.isBlank()) {
            context = "未检索到相关文档";
        }
        String history = shortTermMemory.contextWindow(state.getSessionId());
        String memory = state.getMemoryContext() == null ? "" : state.getMemoryContext();
        String prompt = RAG_PROMPT.formatted(history, memory, context, state.getUserMessage());

        String answer = tracer.trace(state.getSessionId(), "knowledge_rag", "generate", Map.of("hits", ranked.size()),
                () -> generate(prompt, sink));
        state.getSubResults().put("knowledge_rag", answer);
        sink.step("knowledge_rag", "done", Map.of());
        return state;
    }

    /**
     * Query 改写：短句 / 含指代（这个、那个、它…）时先尝试 LLM 改写，
     * 失败或关闭时退化为启发式（拼上一轮用户问题）。
     */
    private String rewriteQuery(AgentState state) {
        String message = state.getUserMessage() == null ? "" : state.getUserMessage().trim();
        boolean shortQuery = message.length() <= 12;
        boolean hasReference = containsAny(message, "这个", "那个", "它", "上面", "刚才", "之前", "这些", "那些", "里面", "的话", "上面说的");
        if ((shortQuery || hasReference) && rewriteEnabled) {
            String history = shortTermMemory.contextWindow(state.getSessionId());
            try {
                String rewritten = chatClient.prompt()
                        .system(REWRITE_PROMPT)
                        .user("最近对话：\n" + history + "\n用户问题：" + message)
                        .call()
                        .content();
                if (rewritten != null && !rewritten.isBlank()) {
                    return rewritten.trim();
                }
            } catch (Exception e) {
                log.warn("llm query rewrite failed, fallback heuristic: {}", e.getMessage());
            }
        }
        if (!shortQuery) {
            return message;
        }
        // 启发式兜底：短追问拼上一轮用户问题
        ArrayList<String> users = new ArrayList<>();
        for (String line : shortTermMemory.contextWindow(state.getSessionId()).split("\n")) {
            if (line.startsWith("user:")) {
                users.add(line.substring(5).trim());
            }
        }
        if (users.size() < 2) {
            return message;
        }
        String previous = users.get(users.size() - 2);
        if (previous.isBlank() || previous.equals(message)) {
            return message;
        }
        return previous + " " + message;
    }

    /**
     * LLM 重排：从 TopK 候选中选最相关的 topN；失败/关闭时按相似度分数取前 N。
     */
    private List<RagHit> rerank(List<RagHit> hits, String query) {
        if (!rerankEnabled || hits.size() <= rerankTop) {
            return hits;
        }
        try {
            List<Map<String, Object>> candidates = hits.stream()
                    .map(hit -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", hit.getId());
                        m.put("snippet", hit.getContent() == null ? "" : trim(hit.getContent(), 120));
                        return m;
                    })
                    .toList();
            RerankResult result = chatClient.prompt()
                    .system(RERANK_PROMPT)
                    .user("查询：" + query + "\n候选文档：" + candidates)
                    .call()
                    .entity(RerankResult.class);
            if (result != null && result.ids() != null && !result.ids().isEmpty()) {
                Map<String, RagHit> byId = hits.stream().collect(Collectors.toMap(RagHit::getId, h -> h));
                List<RagHit> ranked = result.ids().stream()
                        .map(byId::get)
                        .filter(Objects::nonNull)
                        .toList();
                if (!ranked.isEmpty()) {
                    return ranked.stream().limit(rerankTop).toList();
                }
            }
        } catch (Exception e) {
            log.warn("llm rerank failed, keep score order: {}", e.getMessage());
        }
        return hits.stream().limit(rerankTop).toList();
    }

    private String generate(String prompt, AgentEventSink sink) {
        try {
            StringBuilder sb = new StringBuilder();
            chatClient.prompt().user(prompt).stream().content()
                    .doOnNext(delta -> {
                        if (delta != null && !delta.isEmpty()) {
                            sink.token(delta);
                            sb.append(delta);
                        }
                    })
                    .blockLast(java.time.Duration.ofSeconds(90));
            if (!sb.isEmpty()) {
                return sb.toString();
            }
        } catch (Exception ignored) {
            // 网关不支持流式时回退一次性生成
        }
        String content = chatClient.prompt().user(prompt).call().content();
        return content == null ? "暂时无法生成回答，请稍后重试。" : content;
    }

    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    /** LLM 重排结构化输出：按相关性排序的文档 id 列表 */
    public record RerankResult(List<String> ids) {
    }
}
