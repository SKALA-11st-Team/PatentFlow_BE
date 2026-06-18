/**
 * @author 유건욱
 * @date 2026-05-20
 */
package com.syuuk.patentflow.auth.service;

import com.syuuk.patentflow.auth.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * @relatedFR FR-COM-01
 * @relatedUI UI-COM-01
 * @description access·refresh 토큰을 HttpOnly 쿠키로 발급/제거/조회하는 인증 쿠키 헬퍼.
 */
@Service
public class AuthCookieService {

    private final AuthProperties properties;

    public AuthCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-COM-01
     * @description access·refresh 토큰을 만료 시각에 맞춰 경로별 HttpOnly 쿠키로 응답에 기록한다.
     */
    public void writeAuthCookies(
            HttpServletResponse response,
            String accessToken,
            Instant accessExpiresAt,
            String refreshToken,
            Instant refreshExpiresAt
    ) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(
                properties.getAccessCookieName(),
                accessToken,
                "/api",
                maxAge(accessExpiresAt)).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(
                properties.getRefreshCookieName(),
                refreshToken,
                "/api/v1/auth",
                maxAge(refreshExpiresAt)).toString());
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-COM-01
     * @description 만료 시각을 0으로 설정한 빈 쿠키를 내려 access·refresh 쿠키를 제거한다.
     */
    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(properties.getAccessCookieName(), "", "/api", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(properties.getRefreshCookieName(), "", "/api/v1/auth", Duration.ZERO).toString());
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-COM-01
     * @description 요청 쿠키에서 access 토큰 값을 추출한다.
     */
    public String getAccessToken(HttpServletRequest request) {
        return getCookieValue(request, properties.getAccessCookieName());
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-COM-01
     * @description 요청 쿠키에서 refresh 토큰 값을 추출한다.
     */
    public String getRefreshToken(HttpServletRequest request) {
        return getCookieValue(request, properties.getRefreshCookieName());
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite(properties.getCookieSameSite())
                .path(path)
                .maxAge(maxAge);
        if (properties.getCookieDomain() != null && !properties.getCookieDomain().isBlank()) {
            builder.domain(properties.getCookieDomain());
        }
        return builder.build();
    }

    private Duration maxAge(Instant expiresAt) {
        Duration duration = Duration.between(Instant.now(), expiresAt);
        return duration.isNegative() ? Duration.ZERO : duration;
    }
}
