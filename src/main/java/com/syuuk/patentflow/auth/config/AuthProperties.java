package com.syuuk.patentflow.auth.config;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "patentflow.auth")
public class AuthProperties {

    private static final String DEV_DEFAULT_JWT_SECRET = "dev-patentflow-jwt-secret-change-me-please-32bytes";

    @Autowired
    private Environment environment;

    private String jwtSecret;
    private long accessTokenExpirationSeconds = 3600;
    private long refreshTokenExpirationSeconds = 1209600;
    private String jwtIssuer = "patentflow";
    private String jwtAudience = "patentflow-api";
    private String jwtKeyId = "default";
    private int maxLoginFailures = 5;
    private long loginLockSeconds = 300;
    private String accessCookieName = "patentflow_access";
    private String refreshCookieName = "patentflow_refresh";
    private boolean cookieSecure = false;
    private String cookieSameSite = "Lax";
    // 비어 있으면 host-only 쿠키(로컬 기본). 크로스 서브도메인 배포에선 ".patentflow.live" 처럼 상위 도메인을 지정한다.
    private String cookieDomain = "";

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationSeconds;
    }

    public void setAccessTokenExpirationSeconds(long accessTokenExpirationSeconds) {
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    }

    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationSeconds;
    }

    public void setRefreshTokenExpirationSeconds(long refreshTokenExpirationSeconds) {
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
    }

    public String getJwtIssuer() {
        return jwtIssuer;
    }

    public void setJwtIssuer(String jwtIssuer) {
        this.jwtIssuer = jwtIssuer;
    }

    public String getJwtAudience() {
        return jwtAudience;
    }

    public void setJwtAudience(String jwtAudience) {
        this.jwtAudience = jwtAudience;
    }

    public String getJwtKeyId() {
        return jwtKeyId;
    }

    public void setJwtKeyId(String jwtKeyId) {
        this.jwtKeyId = jwtKeyId;
    }

    public int getMaxLoginFailures() {
        return maxLoginFailures;
    }

    public void setMaxLoginFailures(int maxLoginFailures) {
        this.maxLoginFailures = maxLoginFailures;
    }

    public long getLoginLockSeconds() {
        return loginLockSeconds;
    }

    public void setLoginLockSeconds(long loginLockSeconds) {
        this.loginLockSeconds = loginLockSeconds;
    }

    public String getAccessCookieName() {
        return accessCookieName;
    }

    public void setAccessCookieName(String accessCookieName) {
        this.accessCookieName = accessCookieName;
    }

    public String getRefreshCookieName() {
        return refreshCookieName;
    }

    public void setRefreshCookieName(String refreshCookieName) {
        this.refreshCookieName = refreshCookieName;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public String getCookieSameSite() {
        return cookieSameSite;
    }

    public void setCookieSameSite(String cookieSameSite) {
        this.cookieSameSite = cookieSameSite;
    }

    public String getCookieDomain() {
        return cookieDomain;
    }

    public void setCookieDomain(String cookieDomain) {
        this.cookieDomain = cookieDomain;
    }

    @PostConstruct
    void validate() {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("patentflow.auth.jwt-secret must be at least 32 characters.");
        }
        if (usesUnsafeDefaultSecretOutsideLocalProfile()) {
            throw new IllegalStateException("PATENTFLOW_JWT_SECRET must be configured outside local/test profiles.");
        }
        if (accessTokenExpirationSeconds <= 0) {
            throw new IllegalStateException("patentflow.auth.access-token-expiration-seconds must be positive.");
        }
        if (refreshTokenExpirationSeconds <= 0) {
            throw new IllegalStateException("patentflow.auth.refresh-token-expiration-seconds must be positive.");
        }
        if (jwtIssuer == null || jwtIssuer.isBlank()) {
            throw new IllegalStateException("patentflow.auth.jwt-issuer must not be blank.");
        }
        if (jwtAudience == null || jwtAudience.isBlank()) {
            throw new IllegalStateException("patentflow.auth.jwt-audience must not be blank.");
        }
        if (jwtKeyId == null || jwtKeyId.isBlank()) {
            throw new IllegalStateException("patentflow.auth.jwt-key-id must not be blank.");
        }
        if (maxLoginFailures <= 0) {
            throw new IllegalStateException("patentflow.auth.max-login-failures must be positive.");
        }
        if (loginLockSeconds <= 0) {
            throw new IllegalStateException("patentflow.auth.login-lock-seconds must be positive.");
        }
    }

    private boolean usesUnsafeDefaultSecretOutsideLocalProfile() {
        if (!DEV_DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            return false;
        }
        String[] activeProfiles = environment == null ? new String[0] : environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return false;
        }
        return Arrays.stream(activeProfiles)
                .noneMatch(profile -> profile.equalsIgnoreCase("local") || profile.equalsIgnoreCase("test"));
    }
}
