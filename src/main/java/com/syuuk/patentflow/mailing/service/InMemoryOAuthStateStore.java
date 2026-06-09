package com.syuuk.patentflow.mailing.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 인메모리 OAuth2 state 저장소(단일 인스턴스 기본값). 멀티레플리카에선 콜백이 다른 파드로 갈 수 있어
 * 검증이 실패하므로 patentflow.redis.enabled=true로 Redis 구현을 사용해야 한다.
 */
@Component
@ConditionalOnProperty(name = "patentflow.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryOAuthStateStore implements OAuthStateStore {

    private final Map<String, Instant> states = new ConcurrentHashMap<>();

    @Override
    public void save(String state, Duration ttl) {
        purgeExpired();
        states.put(state, Instant.now().plus(ttl));
    }

    @Override
    public boolean consume(String state) {
        Instant expiresAt = states.remove(state);
        return expiresAt != null && expiresAt.isAfter(Instant.now());
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        states.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
