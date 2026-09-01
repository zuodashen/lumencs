package com.lumencs.agent;

import com.lumencs.memory.ShortTermMemoryService;
import com.lumencs.modules.skill.SkillRegistry;
import com.lumencs.tracing.AgentTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 闲聊：问候、日常问题。不走 RAG，避免「你好」被当成知识检索或逼用户选业务菜单。
 */
@Component
public class ChitchatAgent {

    private static final Logger log = LoggerFactory.getLogger(ChitchatAgent.class);

    private static final String SYSTEM = """
            你是个人 AI 管家 LUMEN，帮主人记事、查自己的笔记、写博客草稿。用户这轮是闲聊。
            要求：
            - 用中文，简短自然，像家人或同事，不要客服菜单。
            - 可以回答日常问题（你是谁、今天怎么样、闲聊常识）。
            - 不确定的事实不要编；看不到实时数据时要说出来。
            - 若对方其实想办事，轻轻提一句：可以帮你记一笔、加待办、问知识库或写博客。
            - 不要列出编号清单，除非用户明确问「你能做什么」。
            """;

    private final ChatClient chatClient;
    private final ShortTermMemoryService shortTermMemory;
    private final AgentTracer tracer;
    private final SkillRegistry skillRegistry;

    public ChitchatAgent(
            ChatClient chatClient,
            ShortTermMemoryService shortTermMemory,
            AgentTracer tracer,
            SkillRegistry skillRegistry) {
        this.chatClient = chatClient;
        this.shortTermMemory = shortTermMemory;
        this.tracer = tracer;
        this.skillRegistry = skillRegistry;
    }

    public AgentState process(AgentState state, AgentEventSink sink) {
        sink.step("chitchat", "start", Map.of());
        String history = shortTermMemory.contextWindow(state.getSessionId());
        String user = state.getUserMessage() == null ? "" : state.getUserMessage();
        String prompt = "最近对话：\n" + history + "\n用户：\n" + user;
        String answer = tracer.trace(state.getSessionId(), "chitchat", "generate", Map.of(),
                () -> generate(prompt, sink));
        state.getSubResults().put("chitchat", answer);
        sink.step("chitchat", "done", Map.of());
        return state;
    }

    private String generate(String prompt, AgentEventSink sink) {
        try {
            StringBuilder sb = new StringBuilder();
            chatClient.prompt()
                    .system(systemPrompt())
                    .user(prompt)
                    .stream()
                    .content()
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
        } catch (Exception e) {
            log.debug("chitchat stream fallback: {}", e.getMessage());
        }
        try {
            String content = chatClient.prompt().system(systemPrompt()).user(prompt).call().content();
            return content == null || content.isBlank() ? fallbackHello() : content;
        } catch (Exception e) {
            log.warn("chitchat failed: {}", e.getMessage());
            return fallbackHello();
        }
    }

    private String systemPrompt() {
        String sop = skillRegistry.bodyFor("chitchat");
        return sop == null || sop.isBlank() ? SYSTEM : sop;
    }

    private static String fallbackHello() {
        return "你好，我是 LUMEN，你的个人助手。可以帮你记事、查笔记、写博客，也可以点一杯演示奶茶。";
    }
}
