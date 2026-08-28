package com.lumencs.notify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumencs.mapper.InboxEventMapper;
import com.lumencs.mapper.NotifyChannelMapper;
import com.lumencs.mapper.NotifyLogMapper;
import com.lumencs.model.entity.InboxEvent;
import com.lumencs.model.entity.NotifyChannel;
import com.lumencs.model.entity.NotifyLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内收件箱 + Webhook。事件 ID 幂等（Redis SET NX + 表唯一键），对标 PanWatch notify_dedupe。
 */
@Service
public class NotifyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);
    private static final Duration DEDUPE_TTL = Duration.ofHours(24);

    private final InboxEventMapper inboxMapper;
    private final NotifyChannelMapper channelMapper;
    private final NotifyLogMapper logMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RestClient http = RestClient.create();

    public NotifyService(
            InboxEventMapper inboxMapper,
            NotifyChannelMapper channelMapper,
            NotifyLogMapper logMapper,
            StringRedisTemplate redis,
            ObjectMapper objectMapper) {
        this.inboxMapper = inboxMapper;
        this.channelMapper = channelMapper;
        this.logMapper = logMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void publish(String eventType, String eventId, String title, String body) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        if (!acquire(eventId)) {
            return;
        }
        InboxEvent inbox = new InboxEvent();
        inbox.setEventType(eventType);
        inbox.setEventId(eventId);
        inbox.setTitle(title);
        inbox.setBody(body);
        inbox.setReadFlag(false);
        inbox.setCreatedAt(LocalDateTime.now());
        try {
            inboxMapper.insert(inbox);
        } catch (RuntimeException e) {
            log.debug("inbox duplicate {}: {}", eventId, e.getMessage());
            return;
        }
        dispatchWebhooks(eventType, eventId, title, body);
    }

    public List<InboxEvent> listInbox(int limit) {
        return inboxMapper.selectList(new LambdaQueryWrapper<InboxEvent>()
                .orderByDesc(InboxEvent::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
    }

    public long unreadCount() {
        Long n = inboxMapper.selectCount(new LambdaQueryWrapper<InboxEvent>()
                .eq(InboxEvent::getReadFlag, false));
        return n == null ? 0 : n;
    }

    public void markRead(Long id) {
        InboxEvent event = inboxMapper.selectById(id);
        if (event == null) {
            return;
        }
        event.setReadFlag(true);
        inboxMapper.updateById(event);
    }

    public List<NotifyChannel> listChannels() {
        return channelMapper.selectList(new LambdaQueryWrapper<NotifyChannel>().orderByDesc(NotifyChannel::getId));
    }

    public NotifyChannel upsertWebhook(String name, String url, boolean enabled) {
        NotifyChannel existing = channelMapper.selectOne(new LambdaQueryWrapper<NotifyChannel>()
                .eq(NotifyChannel::getType, "WEBHOOK")
                .last("LIMIT 1"));
        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of("url", url == null ? "" : url.trim()));
        } catch (Exception e) {
            json = "{\"url\":\"\"}";
        }
        if (existing == null) {
            NotifyChannel channel = new NotifyChannel();
            channel.setName(name == null || name.isBlank() ? "Webhook" : name);
            channel.setType("WEBHOOK");
            channel.setConfigJson(json);
            channel.setEnabled(enabled);
            channel.setCreatedAt(LocalDateTime.now());
            channel.setUpdatedAt(LocalDateTime.now());
            channelMapper.insert(channel);
            return channel;
        }
        existing.setName(name == null || name.isBlank() ? existing.getName() : name);
        existing.setConfigJson(json);
        existing.setEnabled(enabled);
        existing.setUpdatedAt(LocalDateTime.now());
        channelMapper.updateById(existing);
        return existing;
    }

    public List<NotifyLog> recentLogs() {
        return logMapper.selectList(new LambdaQueryWrapper<NotifyLog>()
                .orderByDesc(NotifyLog::getId)
                .last("LIMIT 30"));
    }

    private boolean acquire(String eventId) {
        try {
            Boolean ok = redis.opsForValue().setIfAbsent("lumencs:notify:dedupe:" + eventId, "1", DEDUPE_TTL);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            return true;
        }
    }

    private void dispatchWebhooks(String eventType, String eventId, String title, String body) {
        List<NotifyChannel> channels = channelMapper.selectList(new LambdaQueryWrapper<NotifyChannel>()
                .eq(NotifyChannel::getType, "WEBHOOK")
                .eq(NotifyChannel::getEnabled, true));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "lumencs");
        payload.put("eventType", eventType);
        payload.put("eventId", eventId);
        payload.put("title", title);
        payload.put("body", body);
        payload.put("ts", System.currentTimeMillis());
        for (NotifyChannel channel : channels) {
            String url = readUrl(channel.getConfigJson());
            NotifyLog row = new NotifyLog();
            row.setChannelId(channel.getId());
            row.setEventType(eventType);
            row.setEventId(eventId);
            row.setCreatedAt(LocalDateTime.now());
            if (url.isBlank()) {
                row.setSuccess(false);
                row.setDetail("webhook url empty");
                logMapper.insert(row);
                continue;
            }
            try {
                http.post().uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();
                row.setSuccess(true);
                row.setDetail("ok");
            } catch (Exception e) {
                row.setSuccess(false);
                row.setDetail(trim(e.getMessage()));
                log.warn("webhook failed: {}", e.getMessage());
            }
            logMapper.insert(row);
        }
    }

    private String readUrl(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(configJson);
            JsonNode url = node.get("url");
            return url == null || url.isNull() ? "" : url.asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private String trim(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 480 ? text : text.substring(0, 480);
    }
}
