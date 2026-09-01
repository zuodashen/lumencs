package com.lumencs.modules.skill;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 一条运行时 Skill：YAML 头信息用于路由，正文按需注入 Prompt。
 */
public record AgentSkill(
        String name,
        String intent,
        String description,
        int priority,
        boolean keyword,
        boolean fallback,
        List<MatchRule> rules,
        List<String> followUp,
        List<String> cancel,
        List<String> cancelExclude,
        String cardHint,
        String cardHintDraft,
        String cardHintRevise,
        String replyDraft,
        String replyRevise,
        String body
) {
    public boolean matches(String message) {
        if (fallback || !keyword || rules == null || rules.isEmpty()) {
            return false;
        }
        String msg = message == null ? "" : message;
        for (MatchRule rule : rules) {
            if (rule.matches(msg)) {
                return true;
            }
        }
        return false;
    }

    public record MatchRule(
            List<String> any,
            List<String> all,
            List<String> none,
            Integer maxLength,
            Pattern regex
    ) {
        boolean matches(String msg) {
            boolean hasCondition = (any != null && !any.isEmpty())
                    || (all != null && !all.isEmpty())
                    || regex != null;
            if (!hasCondition) {
                return false;
            }
            if (regex != null && !regex.matcher(msg).find()) {
                return false;
            }
            if (any != null && !any.isEmpty() && !containsAny(msg, any)) {
                return false;
            }
            if (all != null && !all.isEmpty() && !containsAll(msg, all)) {
                return false;
            }
            if (none != null && containsAny(msg, none)) {
                return false;
            }
            if (maxLength != null && msg.trim().length() > maxLength) {
                return false;
            }
            return true;
        }

        private static boolean containsAny(String text, List<String> keys) {
            for (String key : keys) {
                if (key != null && !key.isBlank() && text.contains(key)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean containsAll(String text, List<String> keys) {
            for (String key : keys) {
                if (key == null || key.isBlank() || !text.contains(key)) {
                    return false;
                }
            }
            return !keys.isEmpty();
        }
    }
}
