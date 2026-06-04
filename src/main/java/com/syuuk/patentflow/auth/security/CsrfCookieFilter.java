package com.syuuk.patentflow.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 지연 로딩되는 CSRF 토큰을 강제로 렌더링해 {@code XSRF-TOKEN} 쿠키가 응답에 실리도록 한다.
 *
 * Spring Security 6는 CSRF 토큰을 deferred 로 처리하므로, 토큰을 읽지 않으면
 * 쿠키가 발급되지 않아 SPA가 {@code X-XSRF-TOKEN} 헤더를 만들 수 없다.
 */
public final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
