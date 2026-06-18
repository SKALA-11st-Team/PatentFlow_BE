/**
 * @author 유건욱
 * @date 2026-05-20
 */
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

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-COM-01
     * @description access 토큰을 만료 시각까지 폐기 저장소에 등록한다.
     */
    public void revoke(String token, Instant expiresAt) {
        store.revoke(token, expiresAt);
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-COM-01
     * @description 토큰이 폐기 목록에 있는지 조회한다(JWT 필터의 토큰 유효성 검사에 사용).
     */
    public boolean isRevoked(String token) {
        return store.isRevoked(token);
    }
}
