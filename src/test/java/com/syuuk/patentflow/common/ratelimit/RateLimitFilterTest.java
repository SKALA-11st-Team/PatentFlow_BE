package com.syuuk.patentflow.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private HttpServletRequest request(String path, String method, String ip) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(path);
        when(request.getMethod()).thenReturn(method);
        when(request.getRemoteAddr()).thenReturn(ip);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        return request;
    }

    @Test
    void blocksWithTooManyRequestsAfterLimitOnAuthEndpoint() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new InMemoryRateLimitStore(), 2, 60);

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
        assertThat(body.toString()).contains("TOO_MANY_REQUESTS");
    }

    @Test
    void separateIpsHaveIndependentLimits() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new InMemoryRateLimitStore(), 1, 60);

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
        RateLimitFilter filter = new RateLimitFilter(new InMemoryRateLimitStore(), 1, 60);

        for (int i = 0; i < 5; i++) {
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(request("/api/v1/patents", "GET", "1.1.1.1"), mock(HttpServletResponse.class), chain);
            verify(chain).doFilter(any(), any());
        }
    }
}
