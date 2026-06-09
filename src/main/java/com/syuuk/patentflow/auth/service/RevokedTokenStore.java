package com.syuuk.patentflow.auth.service;

import java.time.Instant;

/**
 * 폐기된 access 토큰 저장소. 단일 인스턴스에선 인메모리, 멀티레플리카·재시작 환경에선 Redis(공유·영속) 구현을 쓴다.
 * (patentflow.redis.enabled로 선택; 호출부 시그니처는 AuthTokenRevocationService 파사드로 불변)
 */
public interface RevokedTokenStore {

    void revoke(String token, Instant expiresAt);

    boolean isRevoked(String token);
}
