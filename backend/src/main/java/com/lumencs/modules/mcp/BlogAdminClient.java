package com.lumencs.modules.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 调用 lightdiary 管理端写接口。JWT 缓存在进程内，401 时重新登录。
 * 不直连博客 MySQL。未开启写入或未配账号时工具返回明确错误。
 */
@Component
public class BlogAdminClient {

    private static final Logger log = LoggerFactory.getLogger(BlogAdminClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient client;
    private final boolean enabled;
    private final boolean writeEnabled;
    private final String username;
    private final String password;
    private final String publicWebUrl;
    private final AtomicReference<String> authorization = new AtomicReference<>();

    public BlogAdminClient(
            @Value("${lumencs.blog.base-url:}") String baseUrl,
            @Value("${lumencs.blog.write-enabled:false}") boolean writeEnabled,
            @Value("${lumencs.blog.admin-username:}") String username,
            @Value("${lumencs.blog.admin-password:}") String password,
            @Value("${lumencs.blog.public-web-url:}") String publicWebUrl) {
        this.enabled = baseUrl != null && !baseUrl.isBlank();
        this.writeEnabled = writeEnabled;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.publicWebUrl = publicWebUrl == null ? "" : publicWebUrl.replaceAll("/$", "");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(20000);
        this.client = RestClient.builder()
                .baseUrl(enabled ? baseUrl : "http://127.0.0.1")
                .requestFactory(factory)
                .build();
    }

    public boolean writeReady() {
        return enabled && writeEnabled && !username.isBlank() && !password.isBlank();
    }

    public String writeBlockedReason() {
        if (!enabled) {
            return "未配置 BLOG_BASE_URL";
        }
        if (!writeEnabled) {
            return "未开启 BLOG_WRITE_ENABLED，聊天不会写博客";
        }
        if (username.isBlank() || password.isBlank()) {
            return "未配置 BLOG_ADMIN_USERNAME / BLOG_ADMIN_PASSWORD";
        }
        return "";
    }

    public Map<String, Object> createArticle(String title, String summary, String content,
                                             String categoryName, String tagsCsv, boolean publish) {
        Long categoryId = resolveCategoryId(categoryName);
        List<Long> tagIds = resolveTagIds(tagsCsv);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("summary", summary == null ? "" : summary);
        body.put("content", content);
        body.put("categoryId", categoryId);
        body.put("tagIds", tagIds);
        body.put("status", publish ? "PUBLISHED" : "DRAFT");
        body.put("allowComment", 1);
        post("/article/create", body);
        Map<String, Object> created = findArticle(title);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("published", publish);
        result.put("title", title);
        if (created != null) {
            result.put("id", created.get("id"));
            result.put("slug", created.get("slug"));
            result.put("status", created.get("status"));
            Object slug = created.get("slug");
            if (publish && slug != null && !publicWebUrl.isBlank()) {
                result.put("publicUrl", publicWebUrl + "/post/" + slug);
            }
        }
        return result;
    }

    public Map<String, Object> createBookmark(String name, String link, String description, String categoryName) {
        Long categoryId = resolveBookmarkCategoryId(categoryName);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", categoryId);
        body.put("name", name);
        body.put("link", link);
        body.put("description", description == null ? "" : description);
        post("/bookmark/create", body);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("name", name);
        result.put("link", link);
        result.put("category", categoryName);
        return result;
    }

    public Map<String, Object> createTag(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            return Map.of("success", false, "error", "标签名不能为空");
        }
        Long existing = findTagId(trimmed);
        if (existing != null) {
            return Map.of("success", true, "id", existing, "name", trimmed, "created", false);
        }
        post("/tag/create", Map.of("name", trimmed));
        Long id = findTagId(trimmed);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("name", trimmed);
        result.put("created", true);
        if (id != null) {
            result.put("id", id);
        }
        return result;
    }

    private Long resolveCategoryId(String name) {
        String want = name == null || name.isBlank() ? "技术文档" : name.trim();
        Long id = findNamedId(adminList("/category/list"), want);
        if (id != null) {
            return id;
        }
        post("/category/create", Map.of("name", want));
        id = findNamedId(adminList("/category/list"), want);
        if (id == null) {
            throw new IllegalStateException("无法创建或匹配分类：" + want);
        }
        return id;
    }

    private Long resolveBookmarkCategoryId(String name) {
        String want = name == null || name.isBlank() ? "工具" : name.trim();
        Long id = findNamedId(adminList("/bookmarkCategory/list"), want);
        if (id != null) {
            return id;
        }
        post("/bookmarkCategory/create", Map.of("name", want, "sortOrder", 0));
        id = findNamedId(adminList("/bookmarkCategory/list"), want);
        if (id == null) {
            throw new IllegalStateException("无法创建或匹配书签分组：" + want);
        }
        return id;
    }

    private List<Long> resolveTagIds(String tagsCsv) {
        List<Long> ids = new ArrayList<>();
        if (tagsCsv == null || tagsCsv.isBlank()) {
            return ids;
        }
        for (String raw : tagsCsv.split("[,，、\\s]+")) {
            String name = raw.trim();
            if (name.isBlank()) {
                continue;
            }
            Long id = findTagId(name);
            if (id == null) {
                post("/tag/create", Map.of("name", name));
                id = findTagId(name);
            }
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Long findTagId(String name) {
        return findNamedId(adminList("/tag/list"), name);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findArticle(String title) {
        Map<String, Object> body = get("/article/list?pageNum=1&pageSize=20&keyword="
                + java.net.URLEncoder.encode(title, java.nio.charset.StandardCharsets.UTF_8));
        Object data = body.get("data");
        if (data instanceof Map<?, ?> page) {
            Object list = page.get("list");
            if (list instanceof List<?> items) {
                Map<String, Object> best = null;
                for (Object item : items) {
                    if (item instanceof Map<?, ?> row && title.equals(String.valueOf(row.get("title")))) {
                        Map<String, Object> copy = new LinkedHashMap<>();
                        row.forEach((k, v) -> copy.put(String.valueOf(k), v));
                        Long id = asLong(copy.get("id"));
                        Long bestId = best == null ? null : asLong(best.get("id"));
                        if (best == null || (id != null && (bestId == null || id > bestId))) {
                            best = copy;
                        }
                    }
                }
                return best;
            }
        }
        return null;
    }

    private Long findNamedId(List<Map<String, Object>> rows, String name) {
        String want = name.trim();
        for (Map<String, Object> row : rows) {
            if (want.equalsIgnoreCase(String.valueOf(row.getOrDefault("name", "")))) {
                return asLong(row.get("id"));
            }
        }
        for (Map<String, Object> row : rows) {
            if (String.valueOf(row.getOrDefault("name", "")).contains(want)) {
                return asLong(row.get("id"));
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> adminList(String path) {
        Map<String, Object> body = get(path);
        Object data = body.get("data");
        if (data instanceof List<?> items) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : items) {
                if (item instanceof Map<?, ?> row) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    row.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    out.add(copy);
                }
            }
            return out;
        }
        return List.of();
    }

    private Map<String, Object> post(String path, Object body) {
        ensureLogin();
        try {
            return request("POST", path, body, true);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                authorization.set(null);
                ensureLogin();
                return request("POST", path, body, true);
            }
            throw e;
        }
    }

    private Map<String, Object> get(String path) {
        ensureLogin();
        try {
            return request("GET", path, null, true);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                authorization.set(null);
                ensureLogin();
                return request("GET", path, null, true);
            }
            throw e;
        }
    }

    private synchronized void ensureLogin() {
        if (authorization.get() != null) {
            return;
        }
        Map<String, Object> body = request("POST", "/admin/login",
                Map.of("username", username, "password", password), false);
        Object data = body.get("data");
        if (!(data instanceof Map<?, ?> tokenMap)) {
            throw new IllegalStateException("博客登录失败：无 token");
        }
        Object tokenObj = tokenMap.get("token");
        Object headObj = tokenMap.get("tokenHead");
        String token = tokenObj == null ? "" : String.valueOf(tokenObj);
        String head = headObj == null ? "Bearer " : String.valueOf(headObj).trim();
        if (token.isBlank() || "null".equals(token)) {
            throw new IllegalStateException("博客登录失败：用户名或密码不对");
        }
        String prefix = head.endsWith(" ") ? head : head + " ";
        authorization.set(prefix + token);
        log.info("lightdiary admin token refreshed");
    }

    private Map<String, Object> request(String method, String path, Object body, boolean auth) {
        RestClient.ResponseSpec spec;
        if ("GET".equals(method)) {
            var req = client.get().uri(path);
            if (auth && authorization.get() != null) {
                req = req.header("Authorization", authorization.get());
            }
            spec = req.retrieve();
        } else {
            var req = client.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body == null ? Map.of() : body);
            if (auth && authorization.get() != null) {
                req = req.header("Authorization", authorization.get());
            }
            spec = req.retrieve();
        }
        Map<String, Object> parsed = spec.body(MAP);
        if (parsed == null) {
            throw new IllegalStateException("博客接口无响应：" + path);
        }
        Object code = parsed.get("code");
        if (code != null && !"200".equals(String.valueOf(code))) {
            throw new IllegalStateException("博客接口失败 " + path + "：" + parsed.getOrDefault("message", code));
        }
        return parsed;
    }

    private static Long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
