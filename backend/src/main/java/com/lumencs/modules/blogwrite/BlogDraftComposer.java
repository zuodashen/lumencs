package com.lumencs.modules.blogwrite;

import com.lumencs.agent.AgentState;
import com.lumencs.memory.ShortTermMemoryService;
import com.lumencs.modules.mcp.BlogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 发文前用聊天模型起草标题、摘要、Markdown 正文，填进确认卡片。
 * 参考 zbp 的 slot_draft：先起草槽位，用户确认后再调写工具。
 */
@Component
public class BlogDraftComposer {

    private static final Logger log = LoggerFactory.getLogger(BlogDraftComposer.class);

    private final ChatClient chatClient;
    private final ShortTermMemoryService shortTermMemory;
    private final BlogClient blogClient;

    public BlogDraftComposer(
            ChatClient chatClient,
            ShortTermMemoryService shortTermMemory,
            BlogClient blogClient) {
        this.chatClient = chatClient;
        this.shortTermMemory = shortTermMemory;
        this.blogClient = blogClient;
    }

    public ArticleDraft compose(AgentState state) {
        String catalogs = catalogs();
        String history = shortTermMemory.contextWindow(state.getSessionId());
        String user = state.getUserMessage() == null ? "" : state.getUserMessage();
        String prompt = """
                已有分类与标签（优先复用名字，不要生造太多）：
                %s

                最近对话：
                %s
                用户这一句：
                %s
                """.formatted(catalogs, history, user);
        try {
            ArticleDraft draft = chatClient.prompt()
                    .system("""
                            你是个人博客编辑助手。根据用户口述起草一篇中文技术博文。
                            只返回 JSON：{"title":"...","summary":"...","content":"...","tags":"...","category":"...","action":"存草稿"}
                            要求：
                            - title 简洁，不超过 40 字。
                            - summary 一两句。
                            - content 必须是完整 Markdown（含二级标题），把用户提到的要点写清楚；素材不够就明确写成「待补充」小节，不要编造经历和数据。
                            - tags 用中文逗号分隔，尽量从已有标签里选。
                            - category 尽量从已有分类里选，默认「技术文档」。
                            - action 只能是「存草稿」或「发布到前台」。用户没说发布就用「存草稿」。
                            - 不要承诺收益、保本；不要客服套话。
                            """)
                    .user(prompt)
                    .call()
                    .entity(ArticleDraft.class);
            if (draft != null && draft.content() != null && !draft.content().isBlank()) {
                return sanitize(draft);
            }
        } catch (Exception e) {
            log.warn("blog draft compose failed: {}", e.getMessage());
        }
        return fallback(user);
    }

    private String catalogs() {
        List<Map<String, Object>> cats = blogClient.listCategories();
        List<Map<String, Object>> tags = blogClient.listTags();
        String catNames = cats.stream()
                .map(row -> String.valueOf(row.getOrDefault("name", "")))
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("、"));
        String tagNames = tags.stream()
                .map(row -> String.valueOf(row.getOrDefault("name", "")))
                .filter(s -> !s.isBlank())
                .limit(20)
                .collect(Collectors.joining("、"));
        return "分类：" + (catNames.isBlank() ? "技术文档" : catNames)
                + "\n标签：" + (tagNames.isBlank() ? "（空）" : tagNames);
    }

    private ArticleDraft sanitize(ArticleDraft draft) {
        String action = draft.action() != null && draft.action().contains("发布") ? "发布到前台" : "存草稿";
        return new ArticleDraft(
                blankTo(draft.title(), "未命名草稿"),
                blankTo(draft.summary(), ""),
                draft.content().trim(),
                blankTo(draft.tags(), ""),
                blankTo(draft.category(), "技术文档"),
                action
        );
    }

    private ArticleDraft fallback(String user) {
        String title = user == null || user.isBlank() ? "未命名草稿" : user.replaceAll("\\s+", " ").trim();
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        String body = """
                ## 背景

                %s

                ## 待补充

                请在卡片里改正文后再提交。
                """.formatted(user == null ? "" : user);
        return new ArticleDraft(title, "", body, "", "技术文档", "存草稿");
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record ArticleDraft(
            String title,
            String summary,
            String content,
            String tags,
            String category,
            String action
    ) {
    }
}
