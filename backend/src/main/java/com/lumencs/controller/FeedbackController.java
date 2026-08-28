package com.lumencs.controller;

import com.lumencs.common.ApiResponse;
import com.lumencs.common.R;
import com.lumencs.model.dto.FeedbackRequest;
import com.lumencs.model.entity.Feedback;
import com.lumencs.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/feedback")
    public R<Feedback> submit(@Valid @RequestBody FeedbackRequest request) {
        return ApiResponse.ok(feedbackService.submit(
                request.getSessionId(),
                request.getMessageId(),
                request.getScore(),
                Boolean.TRUE.equals(request.getCited()),
                request.getComment()
        ));
    }
}
