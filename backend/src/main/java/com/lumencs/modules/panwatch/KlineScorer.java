package com.lumencs.modules.panwatch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从 K 线摘要打简易分，口径贴近盯盘侠前端（未持仓：≥3 买入，≤-2 回避）。 */
public final class KlineScorer {

    private KlineScorer() {}

    public static Map<String, Object> score(Map<String, Object> summary) {
        Map<String, Object> src = summary == null ? Map.of() : summary;
        int points = 0;
        List<Map<String, Object>> tags = new ArrayList<>();

        String trend = text(src, "trend");
        if (trend.contains("多头")) {
            points += 2;
            tags.add(tag("均线多头", "up"));
        } else if (trend.contains("空头")) {
            points -= 2;
            tags.add(tag("均线空头", "down"));
        }

        String macd = text(src, "macd_status");
        if (macd.contains("金叉")) {
            points += 2;
            tags.add(tag("MACD金叉", "up"));
        } else if (macd.contains("死叉")) {
            points -= 2;
            tags.add(tag("MACD死叉", "down"));
        }
        Double hist = num(src.get("macd_hist"));
        if (hist != null) {
            if (hist > 0) {
                points += 1;
                tags.add(tag("MACD柱为正", "up"));
            } else if (hist < 0) {
                points -= 1;
                tags.add(tag("MACD柱为负", "down"));
            }
        }

        String rsi = text(src, "rsi_status");
        if (rsi.contains("超卖") || rsi.contains("偏强")) {
            points += 1;
            tags.add(tag(rsi.contains("超卖") ? "RSI超卖" : "RSI偏强", "up"));
        } else if (rsi.contains("超买") || rsi.contains("偏弱")) {
            points -= 1;
            tags.add(tag(rsi.contains("超买") ? "RSI超买" : "RSI偏弱", "down"));
        } else if (rsi.contains("中性")) {
            tags.add(tag("RSI中性", "neutral"));
        }

        String kdj = text(src, "kdj_status");
        if (kdj.contains("金叉")) {
            points += 1;
            tags.add(tag("KDJ金叉", "up"));
        } else if (kdj.contains("死叉")) {
            points -= 1;
            tags.add(tag("KDJ死叉", "down"));
        }

        String vol = text(src, "volume_trend");
        if (vol.contains("放量")) {
            points += 1;
            tags.add(tag("放量", "up"));
        } else if (vol.contains("缩量")) {
            points -= 1;
            tags.add(tag("缩量", "down"));
        }

        String pattern = text(src, "kline_pattern");
        if (pattern.contains("吞没") && (pattern.contains("阳") || pattern.contains("多"))) {
            tags.add(tag(pattern, "up"));
        } else if (!pattern.isBlank()) {
            tags.add(tag(pattern, "neutral"));
        }

        Double support = num(src.get("support"));
        Double resistance = num(src.get("resistance"));
        if (support != null) {
            tags.add(tag("支撑 " + fmt(support), "down"));
        }
        if (resistance != null) {
            tags.add(tag("压力 " + fmt(resistance), "up"));
        }

        String action;
        String actionLabel;
        if (points >= 3) {
            action = "buy";
            actionLabel = "买入";
        } else if (points <= -2) {
            action = "avoid";
            actionLabel = "回避";
        } else {
            action = "watch";
            actionLabel = "观望";
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", action);
        out.put("actionLabel", actionLabel);
        out.put("score", points);
        out.put("tags", tags);
        out.put("reason", reason(actionLabel, rsi, trend));
        return out;
    }

    private static String reason(String actionLabel, String rsi, String trend) {
        if (!rsi.isBlank() && !trend.isBlank()) {
            return actionLabel + "：趋势「" + trend + "」，RSI「" + rsi + "」。仅供参考，不是投资建议。";
        }
        return actionLabel + "。仅供参考，不是投资建议。";
    }

    private static Map<String, Object> tag(String label, String tone) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("tone", tone);
        return row;
    }

    private static String text(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null || "null".equals(String.valueOf(v)) ? "" : String.valueOf(v);
    }

    private static Double num(Object raw) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (Exception e) {
            return null;
        }
    }

    private static String fmt(double value) {
        return String.format("%.2f", value);
    }
}
