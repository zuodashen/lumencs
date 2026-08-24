package com.lumencs.tracing;

import com.lumencs.model.entity.TraceSpan;
import com.lumencs.mapper.TraceSpanMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class AgentTracer {

    private static final Logger log = LoggerFactory.getLogger(AgentTracer.class);
    private final TraceSpanMapper spanMapper;
    private final ObjectMapper objectMapper;

    public AgentTracer(TraceSpanMapper spanMapper, ObjectMapper objectMapper) {
        this.spanMapper = spanMapper;
        this.objectMapper = objectMapper;
    }

    public <T> T trace(String sessionId, String agent, String method, Map<String, Object> detail, Supplier<T> action) {
        long start = System.currentTimeMillis();
        boolean success = true;
        try {
            return action.get();
        } catch (RuntimeException e) {
            success = false;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - start;
            persist(sessionId, agent, method, success ? "ok" : "error", duration, detail);
            log.info("agent[{}].{} {}ms success={}", agent, method, duration, success);
        }
    }

    public void persist(String sessionId, String agent, String method, String status, long durationMs, Map<String, Object> detail) {
        TraceSpan span = new TraceSpan();
        span.setSessionId(sessionId);
        span.setAgent(agent);
        span.setMethod(method);
        span.setStatus(status);
        span.setDurationMs(durationMs);
        span.setCreatedAt(LocalDateTime.now());
        if (detail != null && !detail.isEmpty()) {
            try {
                span.setDetailJson(objectMapper.writeValueAsString(detail));
            } catch (JsonProcessingException ignored) {
                span.setDetailJson("{}");
            }
        }
        spanMapper.insert(span);
    }
}
