package com.lumencs.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumencs.common.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 聊天接口限流拦截器：按 IP + sessionId（GET 查询参数）双维度计数，超限返回 429。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int capacity;
    private final int windowSeconds;

    public RateLimitInterceptor(
            RateLimitService rateLimitService,
            ObjectMapper objectMapper,
            @Value("${lumencs.ratelimit.enabled}") boolean enabled,
            @Value("${lumencs.ratelimit.capacity}") int capacity,
            @Value("${lumencs.ratelimit.window-seconds}") int windowSeconds) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.capacity = capacity;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!enabled) {
            return true;
        }
        String ip = clientIp(request);
        boolean ipOk = rateLimitService.allow("ip:" + ip, capacity, windowSeconds);
        String session = request.getParameter("sessionId");
        boolean sessionOk = session == null || session.isBlank()
                || rateLimitService.allow("session:" + session, capacity, windowSeconds);
        if (ipOk && sessionOk) {
            return true;
        }
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(429, "请求过于频繁，请稍后再试")));
        return false;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
