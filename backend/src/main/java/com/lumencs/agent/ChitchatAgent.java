package com.lumencs.agent;

import com.lumencs.memory.ShortTermMemoryService;
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
            你是 LumenCS 的对话助手，名字可以叫 LUMEN。用户这轮是闲聊或日常问题，不是办业务。
            要求：
            - 用中文，简短自然，像同事对话，不要客服话术菜单。
            - 可以回答日常问题（天气感受、你是谁、今天怎么样、闲聊常识）。
            - 不确定的事实不要编；需要实时信息时诚实说你看不到实时数据。
            - 不要承诺收益、保本、零风险。
            - 若对方其实想办事，轻轻提一句：也可以帮你查产品、退款、工单或点奶茶。
            - 不要列出 1～7 的意图清单，除非用户明确问「你能做什么」。
            """;

    private final ChatClient chatClient;
    private final ShortTermMemoryService shortTermMemory;
    private final AgentTracer tracer;

    public ChitchatAgent(
            ChatClient chatClient,
            ShortTermMemoryService shortTermMemory,
            AgentTracer tracer) {
        this.chatClient = chatClient;
        this.shortTermMemory = shortTermMemory;
        this.tracer = tracer;
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
                    .system(SYSTEM)
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
            String content = chatClient.prompt().system(SYSTEM).user(prompt).call().content();
            return content == null || content.isBlank() ? fallbackHello() : content;
        } catch (Exception e) {
            log.warn("chitchat failed: {}", e.getMessage());
            return fallbackHello();
        }
    }

    private static String fallbackHello() {
        return "你好，我是 LUMEN。想聊天也可以，办退款、查产品或点奶茶直接说就行。";
    }
}
