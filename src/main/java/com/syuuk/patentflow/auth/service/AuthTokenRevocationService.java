package com.syuuk.patentflow.auth.service;

import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * access 토큰 폐기 파사드. 저장은 RevokedTokenStore(인메모리/Redis)에 위임하므로 호출부
 * (JwtAuthenticationFilter, AuthService)의 시그니처는 불변이고 구현만 선택된다.
 */
@Service
public class AuthTokenRevocationService {

    private final RevokedTokenStore store;

    public AuthTokenRevocationService(RevokedTokenStore store) {
        this.store = store;
    }

    public void revoke(String token, Instant expiresAt) {
        store.revoke(token, expiresAt);
    }

    public boolean isRevoked(String token) {
        return store.isRevoked(token);
    }
}
