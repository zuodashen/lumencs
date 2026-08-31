package com.lumencs.service;

import com.lumencs.model.entity.ChatMessage;
import com.lumencs.mapper.ChatMessageMapper;
import com.lumencs.model.entity.ChatSession;
import com.lumencs.mapper.ChatSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumencs.agent.AgentEventSink;
import com.lumencs.agent.AgentState;
import com.lumencs.agent.SupervisorAgent;
import com.lumencs.modules.blogwrite.BlogWriteGuard;
import com.lumencs.memory.ShortTermMemoryService;
import com.lumencs.memory.WorkingMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ShortTermMemoryService shortTermMemory;
    private final WorkingMemoryService workingMemory;
    private final SupervisorAgent supervisorAgent;
    private final ObjectMapper objectMapper;
    private final Executor sseExecutor;

    public ChatService(
            ChatSessionMapper sessionMapper,
            ChatMessageMapper messageMapper,
            ShortTermMemoryService shortTermMemory,
            WorkingMemoryService workingMemory,
            SupervisorAgent supervisorAgent,
            ObjectMapper objectMapper,
            @Qualifier("sseExecutor") Executor sseExecutor) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.shortTermMemory = shortTermMemory;
        this.workingMemory = workingMemory;
        this.supervisorAgent = supervisorAgent;
        this.objectMapper = objectMapper;
        this.sseExecutor = sseExecutor;
    }

    public SseEmitter stream(String sessionId, String userLabel, String message, String articleSlug, boolean hubOperator) {
        String sid = (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
        ensureSession(sid, userLabel);
        saveMessage(sid, "user", message, null, null);
        shortTermMemory.addMessage(sid, "user", message);
        return run(sid, userLabel, message, false, articleSlug, hubOperator);
    }

    public SseEmitter streamCard(String sessionId, String userLabel, String cardId, String confirmToken,
                                 Map<String, Object> values, boolean hubOperator) {
        String sid = (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
        ensureSession(sid, userLabel);
        String pendingWorkflow = workingMemory.peekPendingWorkflow(sid);
        if (BlogWriteGuard.isWriteIntent(pendingWorkflow) && !hubOperator) {
            return finishPlain(sid, BlogWriteGuard.LOGIN_HINT);
        }
        String consumed = workingMemory.consumeConfirm(sid, cardId, confirmToken);
        if (consumed == null) {
            return finishPlain(sid, BlogWriteGuard.TOKEN_HINT);
        }
        workingMemory.mergeSlots(sid, values);
        String summary = cardSummary(values);
        saveMessage(sid, "user", summary, cardId, null);
        shortTermMemory.addMessage(sid, "user", summary);
        return run(sid, userLabel, summary, true, null, hubOperator);
    }

    private static String cardSummary(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return "已提交办理卡片";
        }
        Object title = values.get("title");
        if (title != null && !title.toString().isBlank()) {
            return "已确认卡片：" + title;
        }
        Object name = values.get("name");
        if (name != null && !name.toString().isBlank()) {
            return "已确认卡片：" + name;
        }
        return "已提交办理卡片";
    }

    private SseEmitter run(String sid, String userLabel, String message, boolean cardSubmit, String articleSlug,
                           boolean hubOperator) {
        SseEmitter emitter = new SseEmitter(180_000L);
        sseExecutor.execute(() -> {
            try {
                send(emitter, "session", Map.of("sessionId", sid));
                AgentState state = new AgentState();
                state.setSessionId(sid);
                state.setUserLabel(userLabel == null || userLabel.isBlank() ? "访客" : userLabel);
                state.setUserMessage(message);
                state.setCardSubmit(cardSubmit);
                state.setArticleSlug(articleSlug);
                state.setHubOperator(hubOperator);
                AgentState result = supervisorAgent.orchestrate(state, new EmitterSink(emitter));
                var saved = saveMessage(sid, "assistant", result.getFinalResponse(), result.getIntent(), result.getCitations());
                shortTermMemory.addMessage(sid, "assistant", result.getFinalResponse());
                Map<String, Object> done = new LinkedHashMap<>();
                done.put("sessionId", sid);
                done.put("messageId", saved == null ? null : saved.getId());
                done.put("content", result.getFinalResponse());
                done.put("intent", result.getIntent());
                done.put("compliancePassed", result.isCompliancePassed());
                done.put("citations", result.getCitations());
                done.put("ticketNo", result.getTicketNo());
                done.put("waitingCard", result.isWaitingCard());
                done.put("reviewPending", result.isReviewPending());
                done.put("reviewId", result.getReviewId());
                done.put("articleSlug", articleSlug == null ? "" : articleSlug);
                if (result.getEmbed() != null && !result.getEmbed().isEmpty()) {
                    done.put("embed", result.getEmbed());
                }
                send(emitter, "message", done);
                send(emitter, "done", Map.of("ok", true));
                emitter.complete();
            } catch (Exception e) {
                log.error("chat stream failed", e);
                try {
                    send(emitter, "error", Map.of("message", friendlyAiError(e)));
                    send(emitter, "done", Map.of("ok", false));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        });
        return emitter;
    }

    private SseEmitter finishPlain(String sid, String text) {
        SseEmitter emitter = new SseEmitter(30_000L);
        try {
            send(emitter, "session", Map.of("sessionId", sid));
            saveMessage(sid, "assistant", text, null, null);
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("sessionId", sid);
            done.put("content", text);
            done.put("intent", "");
            done.put("compliancePassed", true);
            done.put("citations", List.of());
            done.put("waitingCard", false);
            send(emitter, "message", done);
            send(emitter, "done", Map.of("ok", true));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    public List<ChatMessage> history(String sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getId));
    }

    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
        workingMemory.clearWorkflow(sessionId);
    }

    private void ensureSession(String sid, String userLabel) {
        if (sessionMapper.selectById(sid) != null) {
            return;
        }
        ChatSession session = new ChatSession();
        session.setId(sid);
        session.setUserLabel(userLabel == null || userLabel.isBlank() ? "访客" : userLabel);
        sessionMapper.insert(session);
    }

    private ChatMessage saveMessage(String sid, String role, String content, String intent, List<Map<String, Object>> citations) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sid);
        msg.setRole(role);
        msg.setContent(content);
        msg.setIntent(intent);
        msg.setCreatedAt(LocalDateTime.now());
        if (citations != null && !citations.isEmpty()) {
            try {
                msg.setCitationsJson(objectMapper.writeValueAsString(citations));
            } catch (JsonProcessingException ignored) {
                msg.setCitationsJson("[]");
            }
        }
        messageMapper.insert(msg);
        return msg;
    }

    /** 网关 401 时浏览器会把异常断开显示成 network error，这里先转成可读文案再正常结束 SSE。 */
    private static String friendlyAiError(Exception e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        String lower = raw.toLowerCase();
        if (raw.contains("无效的令牌") || lower.contains("incorrect api key") || lower.contains("invalid api key")) {
            return "聊天网关拒绝了密钥（无效的令牌）。请到 DMX 控制台重新复制 API Key，写入项目根目录 .env 的 OPENAI_API_KEY 后执行 docker compose up -d backend。";
        }
        if (lower.contains("401") || lower.contains("unauthorized")) {
            return "聊天网关返回 401，密钥无效或未开通当前模型（" + System.getenv().getOrDefault("MODEL_NAME", "gpt-4o-mini") + "）。";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "聊天网关超时，请稍后重试。";
        }
        return raw.isBlank() ? "处理失败" : raw;
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(objectMapper.writeValueAsString(data), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new IllegalStateException("SSE 发送失败", e);
        }
    }

    private class EmitterSink implements AgentEventSink {
        private final SseEmitter emitter;

        private EmitterSink(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void step(String agent, String status, Map<String, Object> data) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("agent", agent);
            payload.put("status", status);
            payload.put("ts", System.currentTimeMillis());
            if (data != null) {
                payload.putAll(data);
            }
            send(emitter, "step", payload);
        }

        @Override
        public void card(Map<String, Object> card) {
            send(emitter, "card", card);
        }

        @Override
        public void embed(Map<String, Object> embed) {
            send(emitter, "embed", embed);
        }

        @Override
        public void token(String delta) {
            send(emitter, "token", Map.of("delta", delta));
        }
    }
}
