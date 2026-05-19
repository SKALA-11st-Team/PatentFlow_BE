package com.syuuk.patentflow.auth.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "patentflow.auth")
public class AuthProperties {

    private String jwtSecret;
    private long accessTokenExpirationSeconds = 3600;
    private long refreshTokenExpirationSeconds = 1209600;
    private int maxLoginFailures = 5;
    private long loginLockSeconds = 300;
    private String accessCookieName = "patentflow_access";
    private String refreshCookieName = "patentflow_refresh";
    private boolean cookieSecure = false;
    private String cookieSameSite = "Lax";

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

    @PostConstruct
    void validate() {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("patentflow.auth.jwt-secret must be at least 32 characters.");
        }
        if (accessTokenExpirationSeconds <= 0) {
            throw new IllegalStateException("patentflow.auth.access-token-expiration-seconds must be positive.");
        }
        if (refreshTokenExpirationSeconds <= 0) {
            throw new IllegalStateException("patentflow.auth.refresh-token-expiration-seconds must be positive.");
        }
        if (maxLoginFailures <= 0) {
            throw new IllegalStateException("patentflow.auth.max-login-failures must be positive.");
        }
        if (loginLockSeconds <= 0) {
            throw new IllegalStateException("patentflow.auth.login-lock-seconds must be positive.");
        }
    }
}
