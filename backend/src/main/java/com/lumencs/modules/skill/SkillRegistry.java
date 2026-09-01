package com.lumencs.modules.skill;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * 从 classpath skills 目录加载能力包（每个子目录一份 SKILL.md）。
 * 第 1 层：name + description 进意图目录；第 2 层：命中后再读全文。
 */
@Component
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);
    private static final String LOCATION = "classpath*:skills/*/SKILL.md";

    private final List<AgentSkill> skills = new CopyOnWriteArrayList<>();
    private final Yaml yaml = new Yaml();

    @PostConstruct
    public void load() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
            List<AgentSkill> loaded = new ArrayList<>();
            for (Resource resource : resources) {
                if (!resource.exists()) {
                    continue;
                }
                String text = resource.getContentAsString(StandardCharsets.UTF_8);
                AgentSkill skill = parse(text, extraMarkdown(resource));
                if (skill != null) {
                    loaded.add(skill);
                }
            }
            loaded.sort(Comparator.comparingInt(AgentSkill::priority).thenComparing(AgentSkill::name));
            skills.clear();
            skills.addAll(loaded);
            log.info("loaded {} agent skills", skills.size());
        } catch (IOException e) {
            throw new IllegalStateException("failed to load skills from " + LOCATION, e);
        }
    }

    public List<AgentSkill> all() {
        return List.copyOf(skills);
    }

    public Optional<AgentSkill> byIntent(String intent) {
        if (intent == null || intent.isBlank()) {
            return Optional.empty();
        }
        return skills.stream().filter(s -> intent.equals(s.intent())).findFirst();
    }

    public String bodyFor(String intent) {
        return byIntent(intent).map(AgentSkill::body).orElse("");
    }

    public Set<String> intents() {
        Set<String> out = new LinkedHashSet<>();
        for (AgentSkill skill : skills) {
            if (skill.intent() != null && !skill.intent().isBlank()) {
                out.add(skill.intent());
            }
        }
        return out;
    }

    /** 关键词层：按 priority 命中；都未命中则走 fallback skill（知识问答）。 */
    public String matchIntent(String message) {
        for (AgentSkill skill : skills) {
            if (skill.matches(message)) {
                return skill.intent();
            }
        }
        return skills.stream()
                .filter(AgentSkill::fallback)
                .map(AgentSkill::intent)
                .findFirst()
                .orElse("knowledge_rag");
    }

    public String intentCatalogPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是意图识别Agent。只返回 JSON，格式：{\"intent\": \"...\", \"confidence\": 0.0-1.0}\n");
        sb.append("intent 只能是以下之一：\n");
        sb.append(String.join(", ", intents().stream().filter(i -> !"workflow_cancel".equals(i)).toList()));
        sb.append("\n规则（来自 Skill 目录，按 description 判断何时用）：\n");
        for (AgentSkill skill : skills) {
            if (!skill.keyword() && !skill.fallback()) {
                continue;
            }
            sb.append("- ").append(skill.intent()).append("：").append(skill.description().replace('\n', ' ').trim()).append('\n');
        }
        sb.append("""
                confidence：表述明确时接近 1.0。
                闲聊请给 0.7 以上，不要把「你好」标成低置信去澄清。
                只有完全不知道用户要干什么（例如「帮我弄一下」）才把 confidence 压到 0.5 以下。
                """);
        return sb.toString();
    }

    public List<String> cancelPhrases() {
        return byIntent("workflow_cancel").map(AgentSkill::cancel).orElse(List.of());
    }

    public List<String> cancelExclude() {
        return byIntent("workflow_cancel").map(AgentSkill::cancelExclude).orElse(List.of());
    }

    public List<String> followUpPhrases() {
        List<String> out = new ArrayList<>();
        for (AgentSkill skill : skills) {
            if (skill.followUp() != null) {
                out.addAll(skill.followUp());
            }
        }
        return out;
    }

    private String extraMarkdown(Resource skillMd) {
        try {
            Resource sibling = skillMd.createRelative("reference.md");
            if (sibling.exists()) {
                return sibling.getContentAsString(StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            // no appendix
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    AgentSkill parse(String text, String extra) {
        String raw = text == null ? "" : text;
        if (!raw.startsWith("---")) {
            log.warn("skill missing YAML front matter");
            return null;
        }
        int end = raw.indexOf("\n---", 3);
        if (end < 0) {
            log.warn("skill YAML front matter not closed");
            return null;
        }
        String front = raw.substring(3, end).trim();
        String body = raw.substring(end + 4).trim();
        if (extra != null && !extra.isBlank()) {
            body = body + "\n\n" + extra.trim();
        }
        Map<String, Object> fm = yaml.load(front);
        if (fm == null) {
            return null;
        }
        String intent = str(fm.get("intent"));
        if (intent.isBlank()) {
            log.warn("skill missing intent");
            return null;
        }
        List<AgentSkill.MatchRule> rules = parseRules(fm);
        return new AgentSkill(
                str(fm.getOrDefault("name", intent)),
                intent,
                str(fm.get("description")),
                asInt(fm.get("priority"), 100),
                asBool(fm.get("keyword"), true),
                asBool(fm.get("fallback"), false),
                rules,
                strings(fm.get("follow_up")),
                strings(fm.get("cancel")),
                strings(fm.get("cancel_exclude")),
                str(fm.get("card_hint")),
                str(fm.get("card_hint_draft")),
                str(fm.get("card_hint_revise")),
                str(fm.get("reply_draft")),
                str(fm.get("reply_revise")),
                body
        );
    }

    @SuppressWarnings("unchecked")
    private List<AgentSkill.MatchRule> parseRules(Map<String, Object> fm) {
        List<AgentSkill.MatchRule> rules = new ArrayList<>();
        Object rawRules = fm.get("rules");
        if (rawRules instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rules.add(ruleFrom((Map<String, Object>) map));
                }
            }
        }
        List<String> triggers = strings(fm.get("triggers"));
        if (!triggers.isEmpty()) {
            rules.add(new AgentSkill.MatchRule(triggers, List.of(), List.of(), null, null));
        }
        return rules;
    }

    private AgentSkill.MatchRule ruleFrom(Map<String, Object> map) {
        String regex = str(map.get("regex"));
        Pattern pattern = regex.isBlank() ? null : Pattern.compile(regex);
        Integer maxLength = map.get("max_length") instanceof Number n ? n.intValue() : null;
        return new AgentSkill.MatchRule(
                strings(map.get("any")),
                strings(map.get("all")),
                strings(map.get("none")),
                maxLength,
                pattern
        );
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean asBool(Object value, boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return fallback;
    }

    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    out.add(item.toString().trim());
                }
            }
            return out;
        }
        if (value instanceof String s && !s.isBlank()) {
            return List.of(s.trim());
        }
        return List.of();
    }
}
