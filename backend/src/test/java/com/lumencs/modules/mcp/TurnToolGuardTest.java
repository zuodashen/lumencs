package com.lumencs.modules.mcp;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TurnToolGuardTest {

    @Test
    void writeToolBlockedOnChitchatTurn() {
        TurnToolGuard guard = new TurnToolGuard();
        guard.begin("s1", "chitchat", Set.of());
        assertNotNull(guard.rejectReason("s1", "tea_order"));
        guard.end("s1");
    }

    @Test
    void writeAtMostOncePerTurn() {
        TurnToolGuard guard = new TurnToolGuard();
        guard.begin("s1", "milk_tea", Set.of("tea_order"));
        assertNull(guard.rejectReason("s1", "tea_order"));
        guard.markCalled("s1", "tea_order");
        assertNotNull(guard.rejectReason("s1", "tea_order"));
        guard.end("s1");
    }

    @Test
    void otherIntentToolRejected() {
        TurnToolGuard guard = new TurnToolGuard();
        guard.begin("s1", "stock_quote", Set.of("stock_quote"));
        assertNotNull(guard.rejectReason("s1", "blog_article_upsert"));
        assertNull(guard.rejectReason("s1", "stock_quote"));
        guard.end("s1");
    }
}
