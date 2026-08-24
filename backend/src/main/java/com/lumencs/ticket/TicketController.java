package com.lumencs.ticket;

import com.lumencs.common.ApiResponse;
import com.lumencs.common.PageWrapper;
import com.lumencs.common.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** 分页列表（DTO 入、VO 出）。 */
    @GetMapping
    public R<PageWrapper<TicketVO>> list(@Valid TicketQueryDTO query) {
        return ApiResponse.ok(ticketService.listPage(query));
    }

    /** 状态机流转（合法迁移校验）。 */
    @PatchMapping("/{id}")
    public R<TicketVO> update(@PathVariable Long id, @Valid @RequestBody TicketStatusRequest request) {
        return ApiResponse.ok(TicketVO.from(ticketService.updateStatus(id, request.getStatus())));
    }

    /** 保留：供 MCP ticket_query 兜底（全量按单号过滤）。 */
    @GetMapping("/all")
    public R<List<TicketVO>> all() {
        return ApiResponse.ok(ticketService.list().stream().map(TicketVO::from).toList());
    }
}
