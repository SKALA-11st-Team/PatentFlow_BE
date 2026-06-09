package com.syuuk.patentflow.auth.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 인메모리 폐기 저장소(단일 인스턴스 기본값). 재시작·멀티레플리카에선 상태가 분산·소실되므로
 * 운영 멀티레플리카에서는 patentflow.redis.enabled=true로 Redis 구현을 사용해야 한다.
 */
@Component
@ConditionalOnProperty(name = "patentflow.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryRevokedTokenStore implements RevokedTokenStore {

    private final Map<String, Instant> revokedTokens = new ConcurrentHashMap<>();

    @Override
    public void revoke(String token, Instant expiresAt) {
        purgeExpired();
        revokedTokens.put(token, expiresAt);
    }

    @Override
    public boolean isRevoked(String token) {
        Instant expiresAt = revokedTokens.get(token);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            revokedTokens.remove(token);
            return false;
        }
        return true;
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        revokedTokens.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
