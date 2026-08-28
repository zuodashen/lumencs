package com.lumencs.modules.sla;

import com.lumencs.model.entity.Ticket;
import com.lumencs.model.entity.TicketStatus;
import com.lumencs.notify.NotifyService;
import com.lumencs.service.TicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class SlaScheduler {

    private final TicketService ticketService;
    private final NotifyService notifyService;
    private final int waitingMinutes;

    public SlaScheduler(
            TicketService ticketService,
            NotifyService notifyService,
            @Value("${lumencs.sla.waiting-human-minutes}") int waitingMinutes) {
        this.ticketService = ticketService;
        this.notifyService = notifyService;
        this.waitingMinutes = waitingMinutes;
    }

    @Scheduled(fixedDelayString = "${lumencs.sla.scan-ms:60000}")
    public void scan() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(Math.max(1, waitingMinutes));
        for (Ticket ticket : ticketService.list()) {
            if (!TicketStatus.WAITING_HUMAN.name().equals(ticket.getStatus())) {
                continue;
            }
            LocalDateTime mark = ticket.getUpdatedAt() == null ? ticket.getCreatedAt() : ticket.getUpdatedAt();
            if (mark == null || mark.isAfter(deadline)) {
                continue;
            }
            notifyService.publish(
                    "ticket.sla",
                    "ticket.sla." + ticket.getId() + "." + mark.toLocalDate(),
                    "工单等待人工超时",
                    ticket.getTicketNo() + "「" + ticket.getTitle() + "」已超过 " + waitingMinutes + " 分钟仍处于 WAITING_HUMAN。"
            );
        }
    }
}
