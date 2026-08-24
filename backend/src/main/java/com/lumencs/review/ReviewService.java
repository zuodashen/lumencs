package com.lumencs.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumencs.common.PageWrapper;
import com.lumencs.exception.BizException;
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

    public ReviewService(ReviewMapper reviewMapper, ObjectMapper objectMapper) {
        this.reviewMapper = reviewMapper;
        this.objectMapper = objectMapper;
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
        return review;
    }

    @Transactional(rollbackFor = Exception.class)
    public ReviewVO decideVO(Long id, String action, String note) {
        return ReviewVO.from(decide(id, action, note), objectMapper);
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null ? "admin" : auth.getName();
    }
}
