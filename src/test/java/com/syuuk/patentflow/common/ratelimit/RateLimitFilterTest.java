package com.syuuk.patentflow.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

/**
 * P6 BE-RATELIMIT: 인증 엔드포인트 한도 초과 시 429, 한도 내·비대상 경로는 통과.
 */
class RateLimitFilterTest {

    // Spring Boot가 주입하는 ObjectMapper는 JSR-310 모듈이 등록돼 있으므로 테스트에서도 동일하게 맞춘다.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private RateLimitFilter filter(int limit) {
        return new RateLimitFilter(new InMemoryRateLimitStore(), limit, 60, 0, objectMapper);
    }

    private RateLimitFilter filter(int limit, int trustedProxyCount) {
        return new RateLimitFilter(new InMemoryRateLimitStore(), limit, 60, trustedProxyCount, objectMapper);
    }

    private HttpServletRequest request(String path, String method, String ip) {
        return request(path, method, ip, null);
    }

    private HttpServletRequest request(String path, String method, String ip, String forwardedFor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(path);
        when(request.getMethod()).thenReturn(method);
        when(request.getRemoteAddr()).thenReturn(ip);
        when(request.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
        return request;
    }

    @Test
    void blocksWithTooManyRequestsAfterLimitOnAuthEndpoint() throws Exception {
        RateLimitFilter filter = filter(2);

        for (int i = 0; i < 2; i++) {
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(request("/api/v1/auth/login", "POST", "1.1.1.1"), mock(HttpServletResponse.class), chain);
            verify(chain).doFilter(any(), any());
        }

        FilterChain blockedChain = mock(FilterChain.class);
        HttpServletResponse blockedResponse = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(blockedResponse.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilter(request("/api/v1/auth/login", "POST", "1.1.1.1"), blockedResponse, blockedChain);

        verify(blockedChain, never()).doFilter(any(), any());
        verify(blockedResponse).setStatus(429);
        // 429 바디는 표준 ErrorResponse 스키마(code/message/details/timestamp)이며 code는 ErrorCode enum 값이다.
        assertThat(body.toString()).contains("RATE_LIMITED").contains("timestamp").contains("details");
    }

    @Test
    void separateIpsHaveIndependentLimits() throws Exception {
        RateLimitFilter filter = filter(1);

        FilterChain chainA = mock(FilterChain.class);
        filter.doFilter(request("/api/v1/auth/login", "POST", "1.1.1.1"), mock(HttpServletResponse.class), chainA);
        verify(chainA).doFilter(any(), any());

        // 다른 IP는 별도 한도 — 첫 요청은 통과한다.
        FilterChain chainB = mock(FilterChain.class);
        filter.doFilter(request("/api/v1/auth/login", "POST", "2.2.2.2"), mock(HttpServletResponse.class), chainB);
        verify(chainB).doFilter(any(), any());
    }

    @Test
    void nonLimitedPathAlwaysPasses() throws Exception {
        RateLimitFilter filter = filter(1);

        for (int i = 0; i < 5; i++) {
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(request("/api/v1/patents", "GET", "1.1.1.1"), mock(HttpServletResponse.class), chain);
            verify(chain).doFilter(any(), any());
        }
    }

    @Test
    void invitationTokenPathsShareOneBucketPerIp() throws Exception {
        // 토큰이 path 변수라도 같은 IP는 prefix 단위 단일 버킷을 공유해야 한다(토큰마다 새 버킷 금지).
        RateLimitFilter filter = filter(1);

        FilterChain first = mock(FilterChain.class);
        filter.doFilter(request("/api/v1/invitations/token-aaa", "GET", "1.1.1.1"), mock(HttpServletResponse.class), first);
        verify(first).doFilter(any(), any());

        // 다른 토큰이라도 같은 IP·method라면 두 번째 요청은 한도 초과로 차단된다.
        FilterChain blocked = mock(FilterChain.class);
        HttpServletResponse blockedResponse = mock(HttpServletResponse.class);
        when(blockedResponse.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        filter.doFilter(request("/api/v1/invitations/token-bbb", "GET", "1.1.1.1"), blockedResponse, blocked);
        verify(blocked, never()).doFilter(any(), any());
        verify(blockedResponse).setStatus(429);
    }

    @Test
    void invitationGetAndAcceptUseSeparateBuckets() throws Exception {
        // GET 검증과 POST 수락은 서로의 한도를 소진하지 않도록 별도 버킷이어야 한다.
        RateLimitFilter filter = filter(1);

        FilterChain getChain = mock(FilterChain.class);
        filter.doFilter(request("/api/v1/invitations/token-aaa", "GET", "1.1.1.1"), mock(HttpServletResponse.class), getChain);
        verify(getChain).doFilter(any(), any());

        // 같은 IP의 POST 수락은 GET 한도와 무관하게 통과한다.
        FilterChain postChain = mock(FilterChain.class);
        filter.doFilter(request("/api/v1/invitations/accept", "POST", "1.1.1.1"), mock(HttpServletResponse.class), postChain);
        verify(postChain).doFilter(any(), any());
    }

    @Test
    void ignoresForwardedForWhenNoTrustedProxyConfigured() throws Exception {
        // trustedProxyCount=0(기본): XFF를 신뢰하지 않으므로 클라이언트가 헤더를 매번 바꿔도 한도를 우회할 수 없다.
        RateLimitFilter filter = filter(1);

        FilterChain first = mock(FilterChain.class);
        filter.doFilter(
                request("/api/v1/auth/login", "POST", "1.1.1.1", "9.9.9.9"),
                mock(HttpServletResponse.class), first);
        verify(first).doFilter(any(), any());

        // 위조된 XFF leftmost 값을 바꿔도 실제 remoteAddr 기준 한도에 걸린다.
        FilterChain blocked = mock(FilterChain.class);
        HttpServletResponse blockedResponse = mock(HttpServletResponse.class);
        when(blockedResponse.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        filter.doFilter(
                request("/api/v1/auth/login", "POST", "1.1.1.1", "8.8.8.8"),
                blockedResponse, blocked);
        verify(blocked, never()).doFilter(any(), any());
        verify(blockedResponse).setStatus(429);
    }

    @Test
    void usesTrustedHopFromForwardedForWhenProxyConfigured() throws Exception {
        // trustedProxyCount=1: ALB가 덧붙인 '뒤에서 1번째' 값을 클라이언트 IP로 채택한다.
        // 공격자가 leftmost 값을 위조해도(맨 앞) 신뢰 hop(맨 뒤)이 동일하면 한도를 공유한다.
        RateLimitFilter filter = filter(1, 1);

        FilterChain first = mock(FilterChain.class);
        filter.doFilter(
                request("/api/v1/auth/login", "POST", "10.0.0.1", "spoofed, 5.5.5.5"),
                mock(HttpServletResponse.class), first);
        verify(first).doFilter(any(), any());

        // leftmost를 바꿔도 신뢰 hop(5.5.5.5)이 같으므로 같은 버킷 — 두 번째는 차단된다.
        FilterChain blocked = mock(FilterChain.class);
        HttpServletResponse blockedResponse = mock(HttpServletResponse.class);
        when(blockedResponse.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        filter.doFilter(
                request("/api/v1/auth/login", "POST", "10.0.0.1", "another-spoof, 5.5.5.5"),
                blockedResponse, blocked);
        verify(blocked, never()).doFilter(any(), any());
        verify(blockedResponse).setStatus(429);
    }
}
