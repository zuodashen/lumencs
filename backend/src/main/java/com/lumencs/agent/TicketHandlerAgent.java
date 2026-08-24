package com.lumencs.agent;

import com.lumencs.model.entity.Ticket;
import com.lumencs.service.TicketService;
import com.lumencs.tracing.AgentTracer;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TicketHandlerAgent {

    private final TicketService ticketService;
    private final AgentTracer tracer;

    public TicketHandlerAgent(TicketService ticketService, AgentTracer tracer) {
        this.ticketService = ticketService;
        this.tracer = tracer;
    }

    public AgentState process(AgentState state, AgentEventSink sink) {
        sink.step("ticket_handler", "start", Map.of());
        Ticket ticket = tracer.trace(state.getSessionId(), "ticket_handler", "create", Map.of(), () ->
                ticketService.create(
                        state.getSessionId(),
                        state.getUserLabel(),
                        trimTitle(state.getUserMessage()),
                        state.getUserMessage(),
                        "MEDIUM"
                ));
        state.setTicketNo(ticket.getTicketNo());
        String reply = """
                工单已创建。
                工单号：%s
                状态：已创建
                优先级：中等
                请保存工单号，后续可在控制台查看流转。
                """.formatted(ticket.getTicketNo());
        state.getSubResults().put("ticket_handler", reply);
        sink.step("ticket_handler", "done", Map.of("ticketNo", ticket.getTicketNo()));
        return state;
    }

    private String trimTitle(String message) {
        if (message == null || message.isBlank()) {
            return "客户咨询";
        }
        return message.length() <= 40 ? message : message.substring(0, 40);
    }
}
