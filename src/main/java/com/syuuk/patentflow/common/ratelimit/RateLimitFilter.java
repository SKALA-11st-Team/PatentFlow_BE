package com.syuuk.patentflow.common.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * P6 BE-RATELIMIT: 미인증 인증 엔드포인트(login/refresh/logout)에 IP+경로 단위 요청 한도를 적용한다.
 * 계정 락아웃(LoginAttemptService)이 '계정' 단위라면, 본 필터는 '요청' 단위 브루트포스/남용을 막는다.
 * 한도 초과 시 429를 반환한다. patentflow.ratelimit.enabled=false면 비활성(테스트 기본 false).
 */
@Component
@ConditionalOnProperty(name = "patentflow.ratelimit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout");

    private final RateLimitStore store;
    private final int limit;
    private final Duration window;

    public RateLimitFilter(
            RateLimitStore store,
            @Value("${patentflow.ratelimit.auth.limit:30}") int limit,
            @Value("${patentflow.ratelimit.auth.window-seconds:60}") long windowSeconds
    ) {
        this.store = store;
        this.limit = limit;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!"OPTIONS".equalsIgnoreCase(request.getMethod()) && LIMITED_PATHS.contains(path)) {
            String key = clientIp(request) + ":" + path;
            if (!store.tryConsume(key, limit, window)) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
