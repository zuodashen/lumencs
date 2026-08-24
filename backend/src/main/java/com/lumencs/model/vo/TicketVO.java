package com.lumencs.model.vo;

import com.lumencs.model.entity.Ticket;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单出参 VO：禁止 Entity 直接出接口。
 */
@Data
public class TicketVO {
    private Long id;
    private String ticketNo;
    private String sessionId;
    private String userLabel;
    private String title;
    private String description;
    private String status;
    private String priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TicketVO from(Ticket ticket) {
        TicketVO vo = new TicketVO();
        vo.setId(ticket.getId());
        vo.setTicketNo(ticket.getTicketNo());
        vo.setSessionId(ticket.getSessionId());
        vo.setUserLabel(ticket.getUserLabel());
        vo.setTitle(ticket.getTitle());
        vo.setDescription(ticket.getDescription());
        vo.setStatus(ticket.getStatus());
        vo.setPriority(ticket.getPriority());
        vo.setCreatedAt(ticket.getCreatedAt());
        vo.setUpdatedAt(ticket.getUpdatedAt());
        return vo;
    }
}
