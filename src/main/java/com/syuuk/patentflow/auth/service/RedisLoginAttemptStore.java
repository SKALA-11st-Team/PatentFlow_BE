package com.syuuk.patentflow.auth.service;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 로그인 시도 저장소(멀티레플리카 공유 + 재시작 영속). 실패는 INCR + 매 호출 윈도우 EXPIRE
 * (슬라이딩 윈도우 — InMemory 구현과 동일 의미), 잠금은 별도 키에 TTL로 둔다. 멀티레플리카에서도
 * 카운터·잠금이 공유되어 브루트포스 우회를 막는다.
 * (운영 전제: Redis는 PVC AOF 영속으로 새벽 재시작을 견딘다.)
 */
@Component
@ConditionalOnProperty(name = "patentflow.redis.enabled", havingValue = "true")
public class RedisLoginAttemptStore implements LoginAttemptStore {

    private static final String FAIL_PREFIX = "login:fail:";
    private static final String LOCK_PREFIX = "login:lock:";

    private final StringRedisTemplate redisTemplate;

    public RedisLoginAttemptStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public int incrementFailures(String key, Duration window) {
        String failKey = FAIL_PREFIX + key;
        Long count = redisTemplate.opsForValue().increment(failKey);
        // 매 실패마다 윈도우 TTL을 갱신한다(슬라이딩 윈도우 — InMemory 구현과 동일 의미).
        // 마지막 실패 이후 window 동안 추가 실패가 없어야 카운터가 만료·리셋된다.
        redisTemplate.expire(failKey, window);
        return count == null ? 0 : count.intValue();
    }

    @Override
    public void setLock(String key, Duration lockDuration) {
        redisTemplate.opsForValue().set(LOCK_PREFIX + key, "1", lockDuration);
    }

    @Override
    public boolean isLocked(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_PREFIX + key));
    }

    @Override
    public void reset(String key) {
        redisTemplate.delete(FAIL_PREFIX + key);
        redisTemplate.delete(LOCK_PREFIX + key);
    }
}
