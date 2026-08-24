package com.lumencs.chat;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.http.MediaType;
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

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return chatService.stream(request.getSessionId(), request.getUserLabel(), request.getMessage());
    }

    @PostMapping(value = "/card", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter card(@Valid @RequestBody CardRequest request, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return chatService.streamCard(request.getSessionId(), request.getUserLabel(), request.getCardId(), request.getValues());
    }

    @GetMapping("/{sessionId}/messages")
    public List<ChatMessage> messages(@PathVariable String sessionId) {
        return chatService.history(sessionId);
    }

    @Data
    public static class ChatRequest {
        private String sessionId;
        private String userLabel;
        @NotBlank
        private String message;
    }

    @Data
    public static class CardRequest {
        private String sessionId;
        private String userLabel;
        private String cardId;
        private java.util.Map<String, Object> values;
    }
}
