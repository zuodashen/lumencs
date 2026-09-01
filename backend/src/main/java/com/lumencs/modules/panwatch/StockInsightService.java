package com.lumencs.modules.panwatch;

import com.lumencs.memory.WorkingMemoryService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StockInsightService {

    private static final Pattern CN_CODE = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");
    private static final String STRIP = "查一下|帮我查|看看|帮我看|查询|股票|行情|k线|K线|现价|价格|怎么样|多少钱|的|一下|盯盘侠"
            + "|这只票|这支票|这只股票|这支股票|刚才那只|刚才这只|当前这只|这一只"
            + "|可以买入吗|可以买吗|能不能买|能买吗|买入吗|现在能买|买不买|要不要买|适合买吗";

    private final PanWatchClient panWatchClient;
    private final WorkingMemoryService workingMemory;

    public StockInsightService(PanWatchClient panWatchClient, WorkingMemoryService workingMemory) {
        this.panWatchClient = panWatchClient;
        this.workingMemory = workingMemory;
    }

    public Map<String, Object> lookup(String rawQuery) {
        return lookup(rawQuery, null);
    }

    public Map<String, Object> lookup(String rawQuery, String sessionId) {
        if (!panWatchClient.ready()) {
            return Map.of("success", false, "error", panWatchClient.blockedReason());
        }
        boolean buyQuestion = isBuyQuestion(rawQuery);
        String market = marketOf(rawQuery);
        Parsed parsed = parse(rawQuery, market);
        if (useLastSymbol(sessionId, rawQuery, parsed)) {
            String lastSymbol = workingMemory.getString(sessionId, "lastStockSymbol");
            String lastMarket = workingMemory.getString(sessionId, "lastStockMarket");
            parsed = new Parsed(lastSymbol, workingMemory.getString(sessionId, "lastStockName"));
            if (!lastMarket.isBlank()) {
                market = lastMarket;
            }
        }
        if (parsed.symbol.isBlank() && parsed.query.isBlank()) {
            return Map.of("success", false, "error",
                    buyQuestion
                            ? "刚才还没锁定具体一只。先说代码或名称，例如「远东股份」或「600869」。"
                            : "告诉我股票代码或名称，例如「酒鬼酒」或「000799」。");
        }
        try {
            String symbol = parsed.symbol;
            String name = parsed.query;
            if (symbol.isBlank()) {
                List<Map<String, Object>> hits = panWatchClient.search(parsed.query, market);
                if (hits.isEmpty()) {
                    return Map.of("success", false, "error", "盯盘侠没有搜到「" + parsed.query + "」。换代码再试。");
                }
                Map<String, Object> first = hits.get(0);
                symbol = str(first, "symbol");
                name = str(first, "name");
                if (!str(first, "market").isBlank()) {
                    market = str(first, "market");
                }
            }
            Map<String, Object> quote = panWatchClient.quote(symbol, market);
            if (name.isBlank()) {
                name = str(quote, "name");
            }
            Map<String, Object> klineBody = panWatchClient.klines(symbol, market, 60);
            Map<String, Object> summaryBody = panWatchClient.klineSummary(symbol, market);
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = summaryBody.get("summary") instanceof Map<?, ?> map
                    ? cast(map)
                    : Map.of();
            Map<String, Object> score = KlineScorer.score(summary);
            List<Map<String, Object>> news = List.of();
            List<Map<String, Object>> suggestions = List.of();
            try {
                news = panWatchClient.news(symbol, name);
            } catch (Exception ignored) {
                // 新闻失败不影响行情卡
            }
            try {
                suggestions = panWatchClient.suggestions(symbol, market);
            } catch (Exception ignored) {
                // 建议池可能为空
            }

            Map<String, Object> embed = new LinkedHashMap<>();
            embed.put("kind", "stock");
            embed.put("symbol", symbol);
            embed.put("market", market);
            embed.put("name", name.isBlank() ? symbol : name);
            embed.put("quote", quote);
            embed.put("klines", klineBody.getOrDefault("klines", List.of()));
            embed.put("summary", summary);
            embed.put("score", score);
            embed.put("news", trimNews(news));
            embed.put("suggestions", suggestions);
            embed.put("openUrl", panWatchClient.publicWebUrl());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("symbol", symbol);
            result.put("name", embed.get("name"));
            result.put("price", quote.get("current_price"));
            result.put("changePct", quote.get("change_pct"));
            result.put("actionLabel", score.get("actionLabel"));
            result.put("buyQuestion", buyQuestion);
            result.put("embed", embed);
            if (sessionId != null && !sessionId.isBlank()) {
                workingMemory.put(sessionId, "lastStockSymbol", symbol);
                workingMemory.put(sessionId, "lastStockMarket", market);
                workingMemory.put(sessionId, "lastStockName", String.valueOf(embed.get("name")));
            }
            return result;
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage() == null ? "查询失败" : e.getMessage());
        }
    }

    private boolean useLastSymbol(String sessionId, String rawQuery, Parsed parsed) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        if (!parsed.symbol.isBlank()) {
            return false;
        }
        String last = workingMemory.getString(sessionId, "lastStockSymbol");
        if (last.isBlank()) {
            return false;
        }
        if (isReferring(rawQuery)) {
            return true;
        }
        return parsed.query.isBlank() && isBuyQuestion(rawQuery);
    }

    private static boolean isReferring(String raw) {
        String msg = raw == null ? "" : raw;
        return containsAny(msg, "这只票", "这支票", "这只股票", "这支股票", "刚才那只", "刚才这只", "当前这只", "这一只");
    }

    private static boolean isBuyQuestion(String raw) {
        String msg = raw == null ? "" : raw;
        return containsAny(msg, "可以买", "能买", "买入吗", "买不买", "要不要买", "适合买", "现在买");
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private record Parsed(String symbol, String query) {}

    private static Parsed parse(String raw, String market) {
        String msg = raw == null ? "" : raw;
        Matcher matcher = CN_CODE.matcher(msg);
        String symbol = "";
        if (matcher.find() && "CN".equals(market)) {
            symbol = matcher.group(1);
        }
        String query = msg.replaceAll(STRIP, " ")
                .replaceAll(CN_CODE.pattern(), " ")
                .replaceAll("[：:，,。！？?\\s]+", " ")
                .trim();
        return new Parsed(symbol, query);
    }

    private static String marketOf(String raw) {
        String msg = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (msg.contains("港股") || msg.contains("hk")) {
            return "HK";
        }
        if (msg.contains("美股") || msg.contains("us")) {
            return "US";
        }
        return "CN";
    }

    private static List<Map<String, Object>> trimNews(List<Map<String, Object>> news) {
        List<Map<String, Object>> out = new ArrayList<>();
        int limit = Math.min(6, news.size());
        for (int i = 0; i < limit; i++) {
            Map<String, Object> row = news.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", str(row, "title"));
            item.put("source", firstNonBlank(str(row, "source_label"), str(row, "source")));
            item.put("time", str(row, "publish_time"));
            item.put("url", str(row, "url"));
            out.add(item);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((k, v) -> copy.put(String.valueOf(k), v));
        return copy;
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null || "null".equals(String.valueOf(v)) ? "" : String.valueOf(v);
    }

    private static String firstNonBlank(String a, String b) {
        return a == null || a.isBlank() ? (b == null ? "" : b) : a;
    }
}
