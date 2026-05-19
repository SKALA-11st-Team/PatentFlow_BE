package com.syuuk.patentflow.auth.dto;

import java.time.Instant;
public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserPrincipalResponse user,
        String refreshToken
) {

    public LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserPrincipalResponse user) {
        this(accessToken, tokenType, expiresAt, user, null);
    }
}
