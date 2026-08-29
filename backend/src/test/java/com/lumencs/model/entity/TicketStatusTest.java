package com.lumencs.model.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单状态机单元测试：合法流转放行、非法流转拒绝、未知状态报错。
 */
class TicketStatusTest {

    @Test
    void 合法主链路流转() {
        assertTrue(TicketStatus.CREATED.canTransitionTo(TicketStatus.PROCESSING));
        assertTrue(TicketStatus.PROCESSING.canTransitionTo(TicketStatus.WAITING_HUMAN));
        assertTrue(TicketStatus.WAITING_HUMAN.canTransitionTo(TicketStatus.RESOLVED));
        assertTrue(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.CLOSED));
    }

    @Test
    void 任意非终态可升级() {
        for (TicketStatus status : TicketStatus.values()) {
            // ESCALATED 本身与终态 CLOSED 除外
            if (status != TicketStatus.ESCALATED && status != TicketStatus.CLOSED) {
                assertTrue(status.canTransitionTo(TicketStatus.ESCALATED), status + " 应可升级");
            }
        }
    }

    @Test
    void 非法流转被拒绝() {
        assertFalse(TicketStatus.CREATED.canTransitionTo(TicketStatus.RESOLVED));
        assertFalse(TicketStatus.CREATED.canTransitionTo(TicketStatus.WAITING_HUMAN));
        assertFalse(TicketStatus.CLOSED.canTransitionTo(TicketStatus.PROCESSING));
        assertFalse(TicketStatus.CLOSED.canTransitionTo(TicketStatus.RESOLVED));
    }

    @Test
    void 终态不可流转() {
        for (TicketStatus target : TicketStatus.values()) {
            assertFalse(TicketStatus.CLOSED.canTransitionTo(target), "CLOSED 不应流转到 " + target);
        }
    }

    @Test
    void 解析大小写不敏感() {
        assertEquals(TicketStatus.PROCESSING, TicketStatus.parse("processing"));
        assertEquals(TicketStatus.WAITING_HUMAN, TicketStatus.parse(" waiting_human "));
    }

    @Test
    void 中文状态可解析() {
        assertEquals(TicketStatus.PROCESSING, TicketStatus.parseFlexible("进行中"));
        assertEquals(TicketStatus.RESOLVED, TicketStatus.parseFlexible("已完成"));
        assertEquals(TicketStatus.CREATED, TicketStatus.parseFlexible("CREATED"));
    }

    @Test
    void 未知状态抛错() {
        assertThrows(IllegalArgumentException.class, () -> TicketStatus.parse("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> TicketStatus.parse(""));
    }
}
