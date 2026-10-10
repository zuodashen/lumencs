package com.lumencs.modules.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对话补槽：规则抠完仍缺项时，用一次 LLM 按槽位表抽 JSON。
 * 只填空槽，选项必须落在目录里，不替用户确认。
 */
@Component
public class SlotFillAgent {

    private static final Logger log = LoggerFactory.getLogger(SlotFillAgent.class);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public SlotFillAgent(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> fill(WorkflowDef def, String message, Map<String, Object> slots) {
        if (def == null || message == null || message.isBlank()) {
            return Map.of();
        }
        List<String> missing = WorkflowCatalog.missing(def, slots);
        if (missing.isEmpty()) {
            return Map.of();
        }
        try {
            String raw = chatClient.prompt()
                    .system(systemPrompt(def, missing))
                    .user(message)
                    .call()
                    .content();
            return sanitize(def, parse(raw), slots);
        } catch (Exception e) {
            log.debug("slot fill skipped: {}", e.getMessage());
            return Map.of();
        }
    }

    private static String systemPrompt(WorkflowDef def, List<String> missing) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是槽位抽取器。只返回 JSON 对象，不要解释。\n");
        sb.append("流程：").append(def.title()).append("。只需抽取还缺的字段：").append(missing).append('\n');
        sb.append("字段约定：\n");
        for (WorkflowSlot slot : def.slots()) {
            if (!missing.contains(slot.name())) {
                continue;
            }
            sb.append("- ").append(slot.name()).append("（").append(slot.label()).append("）");
            if (slot.options() != null && !slot.options().isEmpty()) {
                sb.append(" 只能是其中之一：").append(slot.options());
            }
            sb.append('\n');
        }
        sb.append("不确定的字段不要写。不要编造用户没说的内容。\n");
        return sb.toString();
    }

    private Map<String, Object> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        String text = raw.trim();
        Matcher matcher = JSON_OBJECT.matcher(text);
        if (matcher.find()) {
            text = matcher.group();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(text, new TypeReference<>() {});
            return map == null ? Map.of() : map;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static Map<String, Object> sanitize(WorkflowDef def, Map<String, Object> extracted,
                                                Map<String, Object> current) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> already = current == null ? Map.of() : current;
        for (WorkflowSlot slot : def.slots()) {
            if (!blank(already.get(slot.name()))) {
                continue;
            }
            Object value = extracted.get(slot.name());
            if (value == null || value.toString().isBlank() || "null".equals(value.toString())) {
                continue;
            }
            String text = value.toString().trim();
            if (slot.options() != null && !slot.options().isEmpty() && !slot.options().contains(text)) {
                continue;
            }
            out.put(slot.name(), text);
        }
        return out;
    }

    private static boolean blank(Object value) {
        return value == null || value.toString().isBlank() || "null".equals(value.toString());
    }
}
