package com.lumencs.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumencs.agent.AgentEventSink;
import com.lumencs.agent.AgentState;
import com.lumencs.agent.SupervisorAgent;
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

    public SseEmitter stream(String sessionId, String userLabel, String message) {
        String sid = (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
        ensureSession(sid, userLabel);
        saveMessage(sid, "user", message, null, null);
        shortTermMemory.addMessage(sid, "user", message);
        return run(sid, userLabel, message, false);
    }

    public SseEmitter streamCard(String sessionId, String userLabel, String cardId, Map<String, Object> values) {
        String sid = (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
        ensureSession(sid, userLabel);
        workingMemory.mergeSlots(sid, values);
        String summary = "已提交办理卡片：" + (values == null ? "{}" : values.toString());
        saveMessage(sid, "user", summary, cardId, null);
        shortTermMemory.addMessage(sid, "user", summary);
        return run(sid, userLabel, summary, true);
    }

    private SseEmitter run(String sid, String userLabel, String message, boolean cardSubmit) {
        SseEmitter emitter = new SseEmitter(180_000L);
        sseExecutor.execute(() -> {
            try {
                send(emitter, "session", Map.of("sessionId", sid));
                AgentState state = new AgentState();
                state.setSessionId(sid);
                state.setUserLabel(userLabel == null || userLabel.isBlank() ? "访客" : userLabel);
                state.setUserMessage(message);
                state.setCardSubmit(cardSubmit);
                AgentState result = supervisorAgent.orchestrate(state, new EmitterSink(emitter));
                saveMessage(sid, "assistant", result.getFinalResponse(), result.getIntent(), result.getCitations());
                shortTermMemory.addMessage(sid, "assistant", result.getFinalResponse());
                Map<String, Object> done = new LinkedHashMap<>();
                done.put("sessionId", sid);
                done.put("content", result.getFinalResponse());
                done.put("intent", result.getIntent());
                done.put("compliancePassed", result.isCompliancePassed());
                done.put("citations", result.getCitations());
                done.put("ticketNo", result.getTicketNo());
                done.put("waitingCard", result.isWaitingCard());
                send(emitter, "message", done);
                send(emitter, "done", Map.of("ok", true));
                emitter.complete();
            } catch (Exception e) {
                log.error("chat stream failed", e);
                try {
                    send(emitter, "error", Map.of("message", e.getMessage() == null ? "处理失败" : e.getMessage()));
                } catch (Exception ignored) {
                    // ignore
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    public List<ChatMessage> history(String sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getId));
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

    private void saveMessage(String sid, String role, String content, String intent, List<Map<String, Object>> citations) {
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
        public void token(String delta) {
            send(emitter, "token", Map.of("delta", delta));
        }
    }
}
