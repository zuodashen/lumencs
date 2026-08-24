package com.lumencs.review;

import com.lumencs.common.ApiResponse;
import com.lumencs.common.PageWrapper;
import com.lumencs.common.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HITL 收件箱：合规不通过的回复待人工审核（DTO 入、VO 出）。
 */
@RestController
@RequestMapping("/api/admin/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public R<PageWrapper<ReviewVO>> list(@Valid ReviewQueryDTO query) {
        return ApiResponse.ok(reviewService.listPage(query));
    }

    @PostMapping("/{id}/decide")
    public R<ReviewVO> decide(@PathVariable Long id, @Valid @RequestBody ReviewDecideRequest request) {
        return ApiResponse.ok(reviewService.decideVO(id, request.getAction(), request.getNote()));
    }
}
