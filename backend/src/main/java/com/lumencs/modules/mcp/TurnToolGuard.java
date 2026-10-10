package com.lumencs.modules.mcp;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本轮工具信封：闲聊/知识问答看不到写出工具；写工具每轮最多一次。
 */
@Component
public class TurnToolGuard {

    private final Map<String, RunEnvelope> envelopes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> writes = new ConcurrentHashMap<>();

    public void begin(String sessionId, String intent, Set<String> allowedTools) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        envelopes.put(sessionId, new RunEnvelope(intent == null ? "" : intent, Set.copyOf(allowedTools)));
        writes.put(sessionId, new AtomicInteger(0));
    }

    public void end(String sessionId) {
        if (sessionId == null) {
            return;
        }
        envelopes.remove(sessionId);
        writes.remove(sessionId);
    }

    public String rejectReason(String sessionId, String tool) {
        if (sessionId == null || tool == null) {
            return null;
        }
        RunEnvelope envelope = envelopes.get(sessionId);
        if (envelope == null) {
            return null;
        }
        if (!envelope.allowedTools().isEmpty() && !envelope.allowedTools().contains(tool)) {
            return "本轮意图「" + envelope.intent() + "」不能调用工具 " + tool;
        }
        if (envelope.allowedTools().isEmpty() && WriteDispatchGuard.isWrite(tool)) {
            return "本轮没有办事信封，不能调用写出工具 " + tool;
        }
        AtomicInteger count = writes.get(sessionId);
        if (WriteDispatchGuard.isWrite(tool) && count != null && count.get() >= 1) {
            return "本轮写操作已用完，不能再调用 " + tool;
        }
        return null;
    }

    public void markCalled(String sessionId, String tool) {
        if (sessionId == null || !WriteDispatchGuard.isWrite(tool)) {
            return;
        }
        AtomicInteger count = writes.get(sessionId);
        if (count != null) {
            count.incrementAndGet();
        }
    }
}
