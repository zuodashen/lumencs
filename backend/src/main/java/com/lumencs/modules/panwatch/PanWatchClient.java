package com.lumencs.modules.panwatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 只走盯盘侠 HTTP。JWT 缓存在进程内，401 再登录。不连它的数据库。
 */
@Component
public class PanWatchClient {

    private static final Logger log = LoggerFactory.getLogger(PanWatchClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient client;
    private final boolean configured;
    private final String username;
    private final String password;
    private final String publicWebUrl;
    private final AtomicReference<String> token = new AtomicReference<>();

    public PanWatchClient(
            @Value("${lumencs.panwatch.base-url:}") String baseUrl,
            @Value("${lumencs.panwatch.username:}") String username,
            @Value("${lumencs.panwatch.password:}") String password,
            @Value("${lumencs.panwatch.public-web-url:}") String publicWebUrl) {
        String origin = baseUrl == null ? "" : baseUrl.replaceAll("/$", "").replaceAll("/api$", "");
        this.configured = !origin.isBlank() && username != null && !username.isBlank()
                && password != null && !password.isBlank();
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.publicWebUrl = publicWebUrl == null || publicWebUrl.isBlank()
                ? origin
                : publicWebUrl.replaceAll("/$", "");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(20000);
        this.client = RestClient.builder()
                .baseUrl(configured ? origin : "http://127.0.0.1")
                .requestFactory(factory)
                .build();
    }

    public boolean ready() {
        return configured;
    }

    public String blockedReason() {
        if (configured) {
            return "";
        }
        return "未配置盯盘侠。在 .env 填写 PANWATCH_BASE_URL、PANWATCH_USERNAME、PANWATCH_PASSWORD 后重启 backend。";
    }

    public String publicWebUrl() {
        return publicWebUrl;
    }

    public List<Map<String, Object>> search(String query, String market) {
        if (!configured || query == null || query.isBlank()) {
            return List.of();
        }
        Object data = get("/api/stocks/search", Map.of(
                "q", query.trim(),
                "market", market == null ? "" : market
        ));
        return listOf(data);
    }

    public Map<String, Object> quote(String symbol, String market) {
        return mapOf(get("/api/quotes/" + symbol, Map.of("market", marketOrCn(market))));
    }

    public Map<String, Object> klines(String symbol, String market, int days) {
        return mapOf(get("/api/klines/" + symbol, Map.of(
                "market", marketOrCn(market),
                "days", String.valueOf(days),
                "interval", "1d"
        )));
    }

    public Map<String, Object> klineSummary(String symbol, String market) {
        return mapOf(get("/api/klines/" + symbol + "/summary", Map.of("market", marketOrCn(market))));
    }

    public List<Map<String, Object>> news(String symbol, String name) {
        Object data = get("/api/news", Map.of(
                "symbols", symbol == null ? "" : symbol,
                "names", name == null ? "" : name,
                "hours", "168",
                "limit", "8",
                "filter_related", "true"
        ));
        return listOf(data);
    }

    public List<Map<String, Object>> suggestions(String symbol, String market) {
        Object data = get("/api/suggestions/" + symbol, Map.of(
                "market", marketOrCn(market),
                "limit", "3",
                "include_expired", "false"
        ));
        return listOf(data);
    }

    /** 盯盘侠已跑过的 TradingAgents 最近一次结果，不在对话里新开 3～5 分钟任务。 */
    public Map<String, Object> latestTradingAgents(String symbol) {
        if (!configured || symbol == null || symbol.isBlank()) {
            return Map.of();
        }
        try {
            Object data = get("/api/agents/tradingagents/latest", Map.of("stock_symbol", symbol.trim()));
            if (!(data instanceof Map<?, ?> map) || map.isEmpty()
                    || (map.get("content") == null && map.get("raw_data") == null)) {
                return Map.of();
            }
            Map<String, Object> raw = map.get("raw_data") instanceof Map<?, ?> nested ? cast(nested) : Map.of();
            Map<String, Object> suggestion = raw.get("suggestion") instanceof Map<?, ?> s ? cast(s) : Map.of();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("date", firstNonBlank(str(map, "analysis_date"), str(map, "updated_at")));
            out.put("title", str(map, "title"));
            String decision = firstNonBlank(str(suggestion, "action"), str(raw, "final_decision"), str(raw, "decision"));
            out.put("decision", decision);
            out.put("decisionLabel", firstNonBlank(str(suggestion, "action_label"), decisionLabel(decision)));
            Object conf = suggestion.get("confidence");
            if (conf == null) {
                conf = raw.get("confidence");
            }
            out.put("confidence", conf);
            String content = str(map, "content");
            if (content.length() > 280) {
                content = content.substring(0, 280) + "…";
            }
            out.put("summary", content);
            return out;
        } catch (Exception e) {
            log.debug("tradingagents latest skipped: {}", e.getMessage());
            return Map.of();
        }
    }

    private static Map<String, Object> cast(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((k, v) -> copy.put(String.valueOf(k), v));
        return copy;
    }

    private static String str(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v == null || "null".equals(String.valueOf(v)) ? "" : String.valueOf(v);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String decisionLabel(String decision) {
        String d = decision == null ? "" : decision.toLowerCase();
        if (d.contains("buy") || d.contains("买")) {
            return "买入";
        }
        if (d.contains("sell") || d.contains("卖")) {
            return "卖出";
        }
        if (d.contains("hold") || d.contains("持有")) {
            return "持有";
        }
        return decision == null ? "" : decision;
    }

    private Object get(String path, Map<String, String> query) {
        return exchange(path, query, true);
    }

    private Object exchange(String path, Map<String, String> query, boolean retryOn401) {
        ensureToken();
        try {
            var spec = client.get().uri(uri -> {
                var b = uri.path(path);
                query.forEach((k, v) -> {
                    if (v != null && !v.isBlank()) {
                        b.queryParam(k, v);
                    }
                });
                return b.build();
            });
            String bearer = token.get();
            if (bearer != null && !bearer.isBlank()) {
                spec = spec.header("Authorization", "Bearer " + bearer);
            }
            Map<String, Object> body = spec.retrieve().body(MAP);
            return unwrap(body);
        } catch (RestClientResponseException e) {
            if (retryOn401 && e.getStatusCode().value() == 401) {
                token.set(null);
                ensureToken();
                return exchange(path, query, false);
            }
            log.warn("panwatch {} failed: {}", path, e.getMessage());
            throw new IllegalStateException(panwatchError(e), e);
        }
    }

    private synchronized void ensureToken() {
        if (!configured) {
            throw new IllegalStateException(blockedReason());
        }
        if (token.get() != null && !token.get().isBlank()) {
            return;
        }
        Map<String, String> login = new LinkedHashMap<>();
        login.put("username", username);
        login.put("password", password);
        try {
            Map<String, Object> body = client.post()
                    .uri("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(login)
                    .retrieve()
                    .body(MAP);
            Object data = unwrap(body);
            if (data instanceof Map<?, ?> map) {
                Object t = map.get("token");
                if (t != null && !String.valueOf(t).isBlank()) {
                    token.set(String.valueOf(t));
                    return;
                }
            }
            throw new IllegalStateException("盯盘侠登录未返回 token");
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(panwatchError(e), e);
        }
    }

    private static Object unwrap(Object body) {
        if (body instanceof Map<?, ?> map) {
            Object data = map.get("data");
            return data == null ? map : data;
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Object data) {
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            return copy;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Object data) {
        if (data instanceof List<?> items) {
            return items.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private static String marketOrCn(String market) {
        return market == null || market.isBlank() ? "CN" : market;
    }

    private static String panwatchError(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body != null && body.contains("未登录")) {
            return "盯盘侠拒绝了登录，请核对 PANWATCH_USERNAME / PANWATCH_PASSWORD";
        }
        return "盯盘侠接口失败：" + (e.getStatusText() == null ? e.getMessage() : e.getStatusText());
    }
}
