package com.syuuk.patentflow.common.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 인메모리 레이트리밋(단일 인스턴스 기본값). 멀티레플리카에선 IP 한도가 레플리카별로 분산되므로
 * 운영 멀티레플리카에서는 patentflow.redis.enabled=true로 Redis 구현을 사용해야 한다.
 */
@Component
@ConditionalOnProperty(name = "patentflow.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryRateLimitStore implements RateLimitStore {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean tryConsume(String key, int limit, Duration window) {
        Instant now = Instant.now();
        Window current = windows.compute(key, (ignored, existing) ->
                (existing == null || existing.expiresAt.isBefore(now)) ? new Window(now.plus(window)) : existing);
        return current.count.incrementAndGet() <= limit;
    }

    private static final class Window {
        private final Instant expiresAt;
        private final AtomicInteger count = new AtomicInteger(0);

        private Window(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
