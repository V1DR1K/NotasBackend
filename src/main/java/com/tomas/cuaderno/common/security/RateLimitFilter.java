package com.tomas.cuaderno.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitProperties properties;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties) { this.properties = properties; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        Limit limit = limitFor(request);
        if (!properties.isEnabled() || limit == null || allowed(request, limit)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(Math.max(1, properties.getWindowSeconds())));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Too many requests. Try again later.\"}");
    }

    private boolean allowed(HttpServletRequest request, Limit limit) {
        long now = Instant.now().getEpochSecond();
        long window = Math.max(1, properties.getWindowSeconds());
        String key = clientAddress(request) + ":" + request.getMethod() + ":" + limit.key();
        Window current = windows.compute(key, (ignored, previous) -> {
            if (previous == null || now - previous.startedAt >= window) return new Window(now, 1);
            return new Window(previous.startedAt, previous.count + 1);
        });
        if (windows.size() > 10000) {
            Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
            while (iterator.hasNext()) {
                if (now - iterator.next().getValue().startedAt >= window) iterator.remove();
            }
        }
        return current.count <= limit.maxRequests();
    }

    private Limit limitFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("/api/auth/login".equals(path)) return new Limit("login", 10);
        if ("/api/auth/refresh".equals(path)) return new Limit("refresh", 20);
        if ("/api/auth/change-password".equals(path)) return new Limit("password", 5);
        if ("/api/search".equals(path)) return new Limit("search", 60);
        if ("POST".equalsIgnoreCase(request.getMethod()) && path.matches("/api/day-entries/[^/]+/analyze")) return new Limit("analysis", 10);
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/files".equals(path)) return new Limit("upload", 20);
        return null;
    }

    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",", 2)[0].trim();
        return request.getRemoteAddr();
    }

    private record Limit(String key, int maxRequests) {}
    private record Window(long startedAt, int count) {}
}
