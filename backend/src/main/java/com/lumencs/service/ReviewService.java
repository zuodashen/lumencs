package com.lumencs.service;

import com.lumencs.model.entity.ChatMessage;
import com.lumencs.model.entity.Review;
import com.lumencs.mapper.ChatMessageMapper;
import com.lumencs.mapper.ReviewMapper;
import com.lumencs.model.dto.ReviewQueryDTO;
import com.lumencs.model.vo.ReviewVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumencs.common.PageWrapper;
import com.lumencs.exception.BizException;
import com.lumencs.notify.NotifyService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HITL 收件箱：合规不通过的回复进入待审队列，管理员通过/驳回后结束。
 */
@Service
public class ReviewService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private final ReviewMapper reviewMapper;
    private final ObjectMapper objectMapper;
    private final ChatMessageMapper messageMapper;
    private final NotifyService notifyService;

    public ReviewService(
            ReviewMapper reviewMapper,
            ObjectMapper objectMapper,
            ChatMessageMapper messageMapper,
            NotifyService notifyService) {
        this.reviewMapper = reviewMapper;
        this.objectMapper = objectMapper;
        this.messageMapper = messageMapper;
        this.notifyService = notifyService;
    }

    /** 分页列表（DTO 入、VO 出），status 可选过滤。 */
    public PageWrapper<ReviewVO> listPage(ReviewQueryDTO query) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .orderByAsc(Review::getStatus)
                .orderByDesc(Review::getId);
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(Review::getStatus, query.getStatus().trim().toUpperCase());
        }
        Page<Review> page = reviewMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<ReviewVO> records = page.getRecords().stream()
                .map(item -> ReviewVO.from(item, objectMapper))
                .toList();
        return PageWrapper.of(page.getTotal(), query.getPageNum(), query.getPageSize(), records);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long enqueue(String sessionId, String originalContent, String intent, List<String> violations) {
        Review review = new Review();
        review.setSessionId(sessionId);
        review.setOriginalContent(originalContent);
        review.setIntent(intent);
        if (violations != null && !violations.isEmpty()) {
            try {
                review.setViolationsJson(objectMapper.writeValueAsString(violations));
            } catch (Exception ignored) {
                review.setViolationsJson("[]");
            }
        }
        review.setStatus(STATUS_PENDING);
        review.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(review);
        notifyService.publish(
                "hitl.pending",
                "hitl.pending." + review.getId(),
                "待人工审核 #" + review.getId(),
                "会话 " + sessionId + " 的回复未通过合规，请到收件箱处理。"
        );
        return review.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public Review decide(Long id, String action, String note) {
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BizException("审核单不存在");
        }
        if (!STATUS_PENDING.equals(review.getStatus())) {
            throw new BizException("该审核单已处理");
        }
        String status = switch (action == null ? "" : action.trim().toUpperCase()) {
            case "APPROVE", "APPROVED" -> STATUS_APPROVED;
            case "REJECT", "REJECTED" -> STATUS_REJECTED;
            default -> throw new BizException("未知审核动作: " + action);
        };
        review.setStatus(status);
        review.setReviewNote(note);
        review.setReviewedBy(currentUser());
        review.setReviewedAt(LocalDateTime.now());
        reviewMapper.updateById(review);
        String outbound = STATUS_APPROVED.equals(status)
                ? (note == null || note.isBlank() ? review.getOriginalContent() : note)
                : "人工审核未通过，该回复不会作为最终答案。" + (note == null || note.isBlank() ? "" : " 说明：" + note);
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(review.getSessionId());
        msg.setRole("assistant");
        msg.setContent(STATUS_APPROVED.equals(status) ? "【审核通过】\n" + outbound : outbound);
        msg.setIntent(review.getIntent());
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
        review.setMessageId(msg.getId());
        reviewMapper.updateById(review);
        notifyService.publish(
                "hitl.decided",
                "hitl.decided." + review.getId() + "." + status,
                "审核单 #" + review.getId() + " 已" + (STATUS_APPROVED.equals(status) ? "通过" : "驳回"),
                "结果已写回访客会话。"
        );
        return review;
    }

    @Transactional(rollbackFor = Exception.class)
    public ReviewVO decideVO(Long id, String action, String note) {
        return ReviewVO.from(decide(id, action, note), objectMapper);
    }

    public long pendingCount() {
        Long n = reviewMapper.selectCount(new LambdaQueryWrapper<Review>().eq(Review::getStatus, STATUS_PENDING));
        return n == null ? 0 : n;
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null ? "admin" : auth.getName();
    }
}
