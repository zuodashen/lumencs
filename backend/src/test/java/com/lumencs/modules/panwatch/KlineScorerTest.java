package com.lumencs.modules.panwatch;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KlineScorerTest {

    @Test
    void 空头死叉打出回避() {
        Map<String, Object> scored = KlineScorer.score(Map.of(
                "trend", "空头排列",
                "macd_status", "死叉",
                "macd_hist", -0.04,
                "rsi_status", "中性",
                "kdj_status", "金叉",
                "support", 38.06,
                "resistance", 42.78
        ));
        assertEquals("avoid", scored.get("action"));
        assertEquals("回避", scored.get("actionLabel"));
        assertEquals(-4, scored.get("score"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tags = (List<Map<String, Object>>) scored.get("tags");
        assertEquals(true, tags.stream().anyMatch(t -> String.valueOf(t.get("label")).contains("支撑")));
    }

    @Test
    void 多头金叉偏买入() {
        Map<String, Object> scored = KlineScorer.score(Map.of(
                "trend", "多头排列",
                "macd_status", "金叉",
                "macd_hist", 0.05,
                "rsi_status", "偏强",
                "volume_trend", "放量"
        ));
        assertEquals("buy", scored.get("action"));
    }
}
