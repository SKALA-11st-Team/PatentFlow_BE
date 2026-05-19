package com.syuuk.patentflow.auth.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "patentflow.auth")
public class AuthProperties {

    private String jwtSecret;
    private long accessTokenExpirationSeconds = 3600;

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

    @PostConstruct
    void validate() {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("patentflow.auth.jwt-secret must be at least 32 characters.");
        }
        if (accessTokenExpirationSeconds <= 0) {
            throw new IllegalStateException("patentflow.auth.access-token-expiration-seconds must be positive.");
        }
    }
}
