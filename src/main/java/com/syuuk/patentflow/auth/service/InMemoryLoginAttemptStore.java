package com.syuuk.patentflow.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 인메모리 로그인 시도 저장소(단일 인스턴스 기본값). 멀티레플리카/재시작에선 우회 가능하므로
 * 운영 멀티레플리카에서는 patentflow.redis.enabled=true로 Redis 구현을 사용해야 한다.
 */
@Component
@ConditionalOnProperty(name = "patentflow.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryLoginAttemptStore implements LoginAttemptStore {

    private final Map<String, State> states = new ConcurrentHashMap<>();

    @Override
    public int incrementFailures(String key, Duration window) {
        Instant now = Instant.now();
        State state = states.compute(key, (ignored, current) -> {
            boolean expired = current != null && current.failuresExpireAt != null
                    && current.failuresExpireAt.isBefore(now);
            State next = (current == null || expired) ? new State() : current;
            next.failures += 1;
            next.failuresExpireAt = now.plus(window);
            return next;
        });
        return state.failures;
    }

    @Override
    public void setLock(String key, Duration lockDuration) {
        states.compute(key, (ignored, current) -> {
            State next = current == null ? new State() : current;
            next.lockedUntil = Instant.now().plus(lockDuration);
            return next;
        });
    }

    @Override
    public boolean isLocked(String key) {
        State state = states.get(key);
        if (state == null || state.lockedUntil == null) {
            return false;
        }
        if (state.lockedUntil.isAfter(Instant.now())) {
            return true;
        }
        states.remove(key);
        return false;
    }

    @Override
    public void reset(String key) {
        states.remove(key);
    }

    private static class State {
        private int failures;
        private Instant failuresExpireAt;
        private Instant lockedUntil;
    }
}
