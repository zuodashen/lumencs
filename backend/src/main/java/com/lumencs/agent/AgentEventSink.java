package com.lumencs.agent;

import java.util.Map;

public interface AgentEventSink {
    void step(String agent, String status, Map<String, Object> data);

    default void card(Map<String, Object> card) {
    }

    default void embed(Map<String, Object> embed) {
    }

    default void token(String delta) {
    }
}
