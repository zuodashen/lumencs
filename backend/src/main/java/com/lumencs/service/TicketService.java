package com.lumencs.service;

import com.lumencs.model.entity.Ticket;
import com.lumencs.mapper.TicketMapper;
import com.lumencs.model.dto.TicketQueryDTO;
import com.lumencs.model.entity.TicketStatus;
import com.lumencs.model.vo.TicketVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumencs.common.PageWrapper;
import com.lumencs.exception.BizException;
import com.lumencs.lock.RedisLockService;
import com.lumencs.notify.NotifyService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class TicketService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    private final TicketMapper ticketMapper;
    private final StringRedisTemplate redis;
    private final RedisLockService lockService;
    private final NotifyService notifyService;

    public TicketService(TicketMapper ticketMapper, StringRedisTemplate redis, RedisLockService lockService,
                         NotifyService notifyService) {
        this.ticketMapper = ticketMapper;
        this.redis = redis;
        this.lockService = lockService;
        this.notifyService = notifyService;
    }

    /** MCP ticket_query 用：按单号在内存中过滤（演示数据量小）。 */
    public List<Ticket> list() {
        return ticketMapper.selectList(new LambdaQueryWrapper<Ticket>().orderByDesc(Ticket::getId));
    }

    /** 控制台工单列表：分页 + 状态过滤，出参 VO。 */
    public PageWrapper<TicketVO> listPage(TicketQueryDTO query) {
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<Ticket>()
                .orderByDesc(Ticket::getId);
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(Ticket::getStatus, query.getStatus().trim().toUpperCase());
        }
        Page<Ticket> page = ticketMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<TicketVO> records = page.getRecords().stream().map(TicketVO::from).toList();
        return PageWrapper.of(page.getTotal(), query.getPageNum(), query.getPageSize(), records);
    }

    /**
     * 创建工单：Redis 日自增 + 分布式锁生成单号，事务保证单号与工单记录一致落库；
     * 审计字段（createTime/updateTime/createUser）由 MetaObjectHandler 自动填充。
     */
    @Transactional(rollbackFor = Exception.class)
    public Ticket create(String sessionId, String userLabel, String title, String description, String priority) {
        Ticket ticket = new Ticket();
        ticket.setTicketNo(nextNo());
        ticket.setSessionId(sessionId);
        ticket.setUserLabel(userLabel == null || userLabel.isBlank() ? "访客" : userLabel);
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setPriority(priority == null ? "MEDIUM" : priority.toUpperCase());
        ticket.setStatus(TicketStatus.CREATED.name());
        ticketMapper.insert(ticket);
        notifyService.publish(
                "ticket.created",
                "ticket.created." + ticket.getTicketNo(),
                "新工单 " + ticket.getTicketNo(),
                ticket.getTitle()
        );
        return ticket;
    }

    /**
     * 状态机流转：只允许合法迁移，非法流转抛 BizException（400）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Ticket updateStatus(Long id, String status) {
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BizException("工单不存在");
        }
        TicketStatus from = TicketStatus.parse(ticket.getStatus());
        TicketStatus to = TicketStatus.parseFlexible(status);
        if (!from.canTransitionTo(to)) {
            throw new BizException("不能从「" + from.zh() + "」改到「" + to.zh() + "」。下一步可以是：" + from.nextZh());
        }
        ticket.setStatus(to.name());
        ticketMapper.updateById(ticket);
        if (to == TicketStatus.WAITING_HUMAN) {
            notifyService.publish(
                    "ticket.waiting",
                    "ticket.waiting." + ticket.getId(),
                    "工单转入人工 " + ticket.getTicketNo(),
                    ticket.getTitle()
            );
        }
        return ticket;
    }

    public Ticket findByNo(String ticketNo) {
        if (ticketNo == null || ticketNo.isBlank()) {
            return null;
        }
        return ticketMapper.selectOne(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getTicketNo, ticketNo.trim()));
    }

    @Transactional(rollbackFor = Exception.class)
    public Ticket updateStatusByNo(String ticketNo, String status) {
        Ticket ticket = findByNo(ticketNo);
        if (ticket == null) {
            throw new BizException("未找到待办 " + ticketNo);
        }
        return updateStatus(ticket.getId(), status);
    }

    /**
     * 单号：TK-yyyyMMdd-四位日自增。加分布式锁防止并发取号撞号；
     * 锁不可用时退化为 UUID 后缀，保证唯一。
     */
    private String nextNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String lockKey = "lumencs:ticket:lock:" + date;
        String seqKey = "lumencs:ticket:seq:" + date;
        String token = RedisLockService.token();
        if (lockService.tryLock(lockKey, token, LOCK_TTL)) {
            try {
                Long seq = redis.opsForValue().increment(seqKey);
                if (seq != null && seq == 1) {
                    redis.expire(seqKey, Duration.ofDays(2));
                }
                return "TK-" + date + "-" + String.format("%04d", seq == null ? 0 : seq);
            } finally {
                lockService.unlock(lockKey, token);
            }
        }
        return "TK-" + date + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
