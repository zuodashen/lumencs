package com.lumencs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumencs.exception.BizException;
import com.lumencs.mapper.ChatMessageMapper;
import com.lumencs.mapper.FeedbackMapper;
import com.lumencs.model.entity.ChatMessage;
import com.lumencs.model.entity.Feedback;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FeedbackService {

    public static final String UP = "UP";
    public static final String DOWN = "DOWN";

    private final FeedbackMapper feedbackMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatClient chatClient;

    public FeedbackService(FeedbackMapper feedbackMapper, ChatMessageMapper messageMapper, ChatClient chatClient) {
        this.feedbackMapper = feedbackMapper;
        this.messageMapper = messageMapper;
        this.chatClient = chatClient;
    }

    public Feedback submit(String sessionId, Long messageId, String score, boolean cited, String comment) {
        if (messageId == null) {
            throw new BizException("缺少 messageId");
        }
        String normalized = score == null ? "" : score.trim().toUpperCase();
        if (!UP.equals(normalized) && !DOWN.equals(normalized)) {
            throw new BizException("score 只能是 UP 或 DOWN");
        }
        ChatMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BizException("消息不存在");
        }
        Feedback existing = feedbackMapper.selectOne(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getMessageId, messageId)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setScore(normalized);
            existing.setCited(cited);
            existing.setComment(comment);
            feedbackMapper.updateById(existing);
            return existing;
        }
        Feedback row = new Feedback();
        row.setSessionId(sessionId == null || sessionId.isBlank() ? message.getSessionId() : sessionId);
        row.setMessageId(messageId);
        row.setScore(normalized);
        row.setCited(cited);
        row.setComment(comment);
        row.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(row);
        return row;
    }

    public List<Map<String, Object>> gaps() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Feedback> downs = feedbackMapper.selectList(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getScore, DOWN)
                .orderByDesc(Feedback::getId)
                .last("LIMIT 40"));
        for (Feedback item : downs) {
            ChatMessage msg = messageMapper.selectById(item.getMessageId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kind", "csat_down");
            row.put("sessionId", item.getSessionId());
            row.put("messageId", item.getMessageId());
            row.put("comment", item.getComment());
            row.put("cited", Boolean.TRUE.equals(item.getCited()));
            row.put("content", msg == null ? "" : trim(msg.getContent(), 280));
            row.put("intent", msg == null ? "" : msg.getIntent());
            row.put("createdAt", item.getCreatedAt());
            result.add(row);
        }
        List<ChatMessage> answers = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getRole, "assistant")
                .eq(ChatMessage::getIntent, "knowledge_rag")
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 40"));
        for (ChatMessage msg : answers) {
            if (hasCitations(msg)) {
                continue;
            }
            boolean already = result.stream().anyMatch(r -> msg.getId().equals(r.get("messageId")));
            if (already) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kind", "no_citation");
            row.put("sessionId", msg.getSessionId());
            row.put("messageId", msg.getId());
            row.put("comment", "");
            row.put("cited", false);
            row.put("content", trim(msg.getContent(), 280));
            row.put("intent", msg.getIntent());
            row.put("createdAt", msg.getCreatedAt());
            result.add(row);
        }
        return result;
    }

    public String draftFaq(String sessionId, Long messageId) {
        ChatMessage target = messageId == null ? null : messageMapper.selectById(messageId);
        List<ChatMessage> thread = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId == null ? (target == null ? "" : target.getSessionId()) : sessionId)
                .orderByAsc(ChatMessage::getId)
                .last("LIMIT 20"));
        if (thread.isEmpty()) {
            throw new BizException("没有可生成草稿的会话");
        }
        StringBuilder conv = new StringBuilder();
        for (ChatMessage msg : thread) {
            conv.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }
        String focus = target == null ? "" : "重点回答这一段助手回复对应的用户问题：\n" + target.getContent();
        String draft = chatClient.prompt()
                .system("""
                        你是技术博主的编辑。根据客服会话写一篇简短 FAQ Markdown。
                        只要正文，不要解释。结构：标题、问题、回答、适用边界。
                        不确定的事实写成「需人工核实」，不要编造数据。
                        """)
                .user("会话：\n" + conv + "\n" + focus)
                .call()
                .content();
        return draft == null || draft.isBlank() ? "暂时无法生成草稿。" : draft;
    }

    public long downCount() {
        Long n = feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>().eq(Feedback::getScore, DOWN));
        return n == null ? 0 : n;
    }

    private boolean hasCitations(ChatMessage msg) {
        String json = msg.getCitationsJson();
        return json != null && json.length() > 4 && json.contains("id");
    }

    private String trim(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
