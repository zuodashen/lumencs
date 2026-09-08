package com.lumencs.modules.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteDispatchGuardTest {

    @Test
    void writeToolsAreGated() {
        assertTrue(WriteDispatchGuard.isWrite("blog_article_upsert"));
        assertTrue(WriteDispatchGuard.isWrite("blog_sync_slug"));
        assertTrue(WriteDispatchGuard.isWrite("tea_order"));
        assertFalse(WriteDispatchGuard.isWrite("stock_quote"));
        assertFalse(WriteDispatchGuard.isWrite("blog_list"));
        assertFalse(WriteDispatchGuard.isWrite("ticket_list"));
    }
}
