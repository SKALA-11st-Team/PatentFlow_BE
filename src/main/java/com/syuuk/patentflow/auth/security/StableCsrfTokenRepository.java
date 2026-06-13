package com.syuuk.patentflow.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DeferredCsrfToken;

/**
 * BE-14: CSRF 토큰을 세션(쿠키) 수명 동안 안정적으로 유지하는 저장소 래퍼.
 *
 * <p>문제: 이 앱은 stateless(JWT)라 매 요청이 새로 인증된다. Spring Security 기본
 * {@code CsrfAuthenticationStrategy}는 인증 성공 시 기존 CSRF 토큰을 삭제({@code saveToken(null)})
 * 후 재발급하는데, 그 결과 <b>인증된 요청마다</b> XSRF-TOKEN 쿠키가 삭제·재생성된다.
 * SPA는 대시보드 폴링 등 동시 요청을 보내므로, 한 요청이 토큰을 회전시키는 사이 다른 변형 요청의
 * "헤더(JS가 읽은 시점 값) ↔ Cookie 헤더(전송 시점 최신 값)"가 어긋나 간헐 403(=핑퐁)이 난다.
 *
 * <p>해결: 더블 서밋 쿠키 방식에서 토큰은 추측 불가능하고 일관되기만 하면 되며 매 요청 회전이 불필요하다.
 * 회전의 삭제 단계({@code saveToken(null)})를 무시하면, 회전 시 기존 토큰이 그대로 유지되어
 * cookie==header가 항상 일치한다. 토큰은 부재 시 1회 생성되고 이후 쿠키 수명 동안 안정적이다.
 *
 * <p>참고: 로그아웃 시 CSRF 쿠키가 삭제되지 않지만, 값 자체가 비민감(cross-origin 공격자가 헤더로
 * 세팅 불가)이라 안전하다. 인증 쿠키(access/refresh)는 별도로 정상 삭제된다.
 */
public final class StableCsrfTokenRepository implements CsrfTokenRepository {

    private final CookieCsrfTokenRepository delegate;

    public StableCsrfTokenRepository(CookieCsrfTokenRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return delegate.generateToken(request);
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        // 회전의 삭제(saveToken(null))는 무시해 토큰을 안정적으로 유지한다.
        if (token == null) {
            return;
        }
        delegate.saveToken(token, request, response);
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        return delegate.loadToken(request);
    }

    @Override
    public DeferredCsrfToken loadDeferredToken(HttpServletRequest request, HttpServletResponse response) {
        return delegate.loadDeferredToken(request, response);
    }
}
