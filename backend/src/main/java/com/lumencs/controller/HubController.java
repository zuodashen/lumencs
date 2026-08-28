package com.lumencs.controller;

import com.lumencs.common.ApiResponse;
import com.lumencs.common.R;
import com.lumencs.mapper.ChatMessageMapper;
import com.lumencs.model.dto.FaqDraftRequest;
import com.lumencs.model.dto.WebhookChannelRequest;
import com.lumencs.model.entity.InboxEvent;
import com.lumencs.model.entity.KbDocument;
import com.lumencs.model.entity.NotifyChannel;
import com.lumencs.model.entity.NotifyLog;
import com.lumencs.notify.NotifyService;
import com.lumencs.rag.RagClient;
import com.lumencs.service.FeedbackService;
import com.lumencs.service.KnowledgeService;
import com.lumencs.service.ReviewService;
import com.lumencs.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/hub")
public class HubController {

    private final NotifyService notifyService;
    private final FeedbackService feedbackService;
    private final ReviewService reviewService;
    private final TicketService ticketService;
    private final KnowledgeService knowledgeService;
    private final RagClient ragClient;
    private final StringRedisTemplate redis;
    private final ChatMessageMapper messageMapper;

    public HubController(
            NotifyService notifyService,
            FeedbackService feedbackService,
            ReviewService reviewService,
            TicketService ticketService,
            KnowledgeService knowledgeService,
            RagClient ragClient,
            StringRedisTemplate redis,
            ChatMessageMapper messageMapper) {
        this.notifyService = notifyService;
        this.feedbackService = feedbackService;
        this.reviewService = reviewService;
        this.ticketService = ticketService;
        this.knowledgeService = knowledgeService;
        this.ragClient = ragClient;
        this.redis = redis;
        this.messageMapper = messageMapper;
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rag", ragClient.healthy() ? "up" : "down");
        body.put("redis", redisUp() ? "up" : "down");
        body.put("documents", knowledgeService.listDocuments().size());
        body.put("tickets", ticketService.list().size());
        body.put("pendingReviews", reviewService.pendingCount());
        body.put("unreadInbox", notifyService.unreadCount());
        body.put("csatDown", feedbackService.downCount());
        body.put("messages", messageMapper.selectCount(null));
        return ApiResponse.ok(body);
    }

    @GetMapping("/inbox")
    public R<List<InboxEvent>> inbox() {
        return ApiResponse.ok(notifyService.listInbox(50));
    }

    @PostMapping("/inbox/{id}/read")
    public R<Void> read(@PathVariable Long id) {
        notifyService.markRead(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/gaps")
    public R<List<Map<String, Object>>> gaps() {
        return ApiResponse.ok(feedbackService.gaps());
    }

    @PostMapping("/faq-draft")
    public R<Map<String, String>> faq(@Valid @RequestBody FaqDraftRequest request) {
        String draft = feedbackService.draftFaq(request.getSessionId(), request.getMessageId());
        return ApiResponse.ok(Map.of("markdown", draft));
    }

    @GetMapping("/channels")
    public R<Map<String, Object>> channels() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channels", notifyService.listChannels());
        body.put("logs", notifyService.recentLogs());
        return ApiResponse.ok(body);
    }

    @PostMapping("/channels/webhook")
    public R<NotifyChannel> webhook(@RequestBody WebhookChannelRequest request) {
        boolean enabled = request.getEnabled() == null || request.getEnabled();
        return ApiResponse.ok(notifyService.upsertWebhook(request.getName(), request.getUrl(), enabled));
    }

    @GetMapping("/scope")
    public R<Map<String, Object>> scope(@RequestParam String slug) {
        KbDocument doc = knowledgeService.findByBlogSlug(slug);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("slug", slug);
        body.put("ready", doc != null);
        body.put("title", doc == null ? slug : doc.getTitle());
        body.put("documentId", doc == null ? null : doc.getId());
        return ApiResponse.ok(body);
    }

    private boolean redisUp() {
        try {
            String pong = redis.getConnectionFactory().getConnection().ping();
            return pong != null;
        } catch (Exception e) {
            return false;
        }
    }
}
