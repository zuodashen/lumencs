package com.lumencs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumencs.model.dto.CardRequest;
import com.lumencs.model.dto.ChatRequest;
import com.lumencs.model.vo.MessageVO;
import com.lumencs.common.ApiResponse;
import com.lumencs.common.R;
import com.lumencs.service.ChatService;
import com.lumencs.security.HubAuth;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public ChatController(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return chatService.stream(request.getSessionId(), request.getUserLabel(), request.getMessage(),
                request.getArticleSlug(), HubAuth.isAdmin());
    }

    @PostMapping(value = "/card", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter card(@Valid @RequestBody CardRequest request, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return chatService.streamCard(request.getSessionId(), request.getUserLabel(), request.getCardId(),
                request.getConfirmToken(), request.getValues(), HubAuth.isAdmin());
    }

    @GetMapping("/{sessionId}/messages")
    public List<MessageVO> messages(@PathVariable String sessionId) {
        return chatService.history(sessionId).stream()
                .map(msg -> MessageVO.from(msg, objectMapper))
                .toList();
    }

    @DeleteMapping("/{sessionId}")
    public R<Void> delete(@PathVariable String sessionId) {
        chatService.deleteSession(sessionId);
        return ApiResponse.ok(null);
    }
}
