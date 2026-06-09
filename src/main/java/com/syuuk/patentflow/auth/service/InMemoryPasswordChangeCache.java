package com.syuuk.patentflow.auth.service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 인메모리 비밀번호 변경 시각 캐시(단일 인스턴스 기본값). TTL 경과 항목은 미스로 처리해 호출부가 DB로 폴백한다.
 * 멀티레플리카에서는 한 레플리카의 write-through가 다른 레플리카에 닿지 않으므로(staleness 윈도우 = TTL),
 * 운영 멀티레플리카에서는 patentflow.redis.enabled=true로 Redis 구현을 사용해 변경을 즉시 공유해야 한다.
 */
@Component
@ConditionalOnProperty(name = "patentflow.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryPasswordChangeCache implements PasswordChangeCache {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<Instant> get(String userId) {
        Entry entry = entries.get(userId);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            entries.remove(userId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public void put(String userId, Instant passwordChangedAt) {
        Instant value = passwordChangedAt == null ? NO_CHANGE : passwordChangedAt;
        entries.put(userId, new Entry(value, Instant.now().plus(TTL)));
    }

    private record Entry(Instant value, Instant expiresAt) {}
}
