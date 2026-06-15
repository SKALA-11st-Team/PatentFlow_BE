package com.syuuk.patentflow.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * P6 BE-RATELIMIT: 미인증 인증 엔드포인트(login/refresh/logout)와 공개 초대 토큰 엔드포인트(/invitations/**)에
 * IP+경로 단위 요청 한도를 적용한다.
 * 계정 락아웃(LoginAttemptService)이 '계정' 단위라면, 본 필터는 '요청' 단위 브루트포스/남용을 막는다.
 * 초대 토큰 검증/수락은 토큰 추측(브루트포스) 표면이므로 동일 한도로 보호한다.
 * 한도 초과 시 429를 반환한다. patentflow.ratelimit.enabled=false면 비활성(테스트 기본 false).
 */
@Component
@ConditionalOnProperty(name = "patentflow.ratelimit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout");
    // 공개 초대 토큰 검증(GET /invitations/{token})·수락(POST /invitations/accept) — 토큰 추측 방지.
    private static final String INVITATION_PREFIX = "/api/v1/invitations";

    private final RateLimitStore store;
    private final int limit;
    private final Duration window;
    // 신뢰 프록시 hop 수. 0이면 X-Forwarded-For를 신뢰하지 않고 getRemoteAddr()만 사용(로컬/테스트 기본).
    // EKS/ALB 배포처럼 앞단 프록시가 있으면 그 hop 수를 지정해 XFF의 '뒤에서 N번째'(클라이언트가 위조할 수 없는
    // 신뢰 hop) 값을 클라이언트 IP로 채택한다. leftmost(클라이언트 제어) 값은 절대 신뢰하지 않는다.
    private final int trustedProxyCount;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(
            RateLimitStore store,
            @Value("${patentflow.ratelimit.auth.limit:30}") int limit,
            @Value("${patentflow.ratelimit.auth.window-seconds:60}") long windowSeconds,
            @Value("${patentflow.ratelimit.trusted-proxy-count:0}") int trustedProxyCount,
            ObjectMapper objectMapper
    ) {
        this.store = store;
        this.limit = limit;
        this.window = Duration.ofSeconds(windowSeconds);
        this.trustedProxyCount = trustedProxyCount;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean invitation = path.startsWith(INVITATION_PREFIX);
        boolean limited = LIMITED_PATHS.contains(path) || invitation;
        if (!"OPTIONS".equalsIgnoreCase(request.getMethod()) && limited) {
            // 초대 GET 검증은 토큰이 path 변수(/invitations/{token})라 path 전체를 키에 쓰면 토큰마다
            // 별도 버킷이 생겨 토큰 추측 한도가 무력화된다. prefix로 정규화해 IP당 단일 버킷을 공유한다.
            // (GET 검증과 POST 수락이 서로의 한도를 소진하지 않도록 method를 키에 포함한다.)
            String keyPath = invitation ? request.getMethod() + ":" + INVITATION_PREFIX : path;
            String key = clientIp(request) + ":" + keyPath;
            if (!store.tryConsume(key, limit, window)) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                objectMapper.writeValue(
                        response.getWriter(),
                        ErrorResponse.of(ErrorCode.RATE_LIMITED, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        if (trustedProxyCount > 0) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                // 뒤에서 trustedProxyCount번째 hop이 신뢰 가능한 클라이언트 IP(앞단 프록시가 덧붙인 값).
                int index = hops.length - trustedProxyCount;
                if (index >= 0 && index < hops.length) {
                    String candidate = hops[index].trim();
                    if (!candidate.isEmpty()) {
                        return candidate;
                    }
                }
            }
        }
        return request.getRemoteAddr();
    }
}
