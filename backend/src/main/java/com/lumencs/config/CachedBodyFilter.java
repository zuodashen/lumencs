package com.lumencs.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 缓存 POST /api/chat 请求体，限流拦截器与 @RequestBody 都能读到 JSON。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CachedBodyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().contains("/api/chat")) {
            chain.doFilter(new RepeatableBodyRequest(request), response);
            return;
        }
        chain.doFilter(request, response);
    }

    public static final class RepeatableBodyRequest extends HttpServletRequestWrapper {
        private final byte[] cached;

        RepeatableBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cached = request.getInputStream().readAllBytes();
        }

        public byte[] cachedBody() {
            return cached;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(cached);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    // 聊天 POST 走阻塞读，不需要异步 IO 回调
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return cached.length;
        }

        @Override
        public long getContentLengthLong() {
            return cached.length;
        }
    }
}
