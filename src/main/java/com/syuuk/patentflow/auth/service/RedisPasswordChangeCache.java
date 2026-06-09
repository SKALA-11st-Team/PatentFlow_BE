package com.syuuk.patentflow.auth.service;

import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 비밀번호 변경 시각 캐시(멀티레플리카 공유). 변경 시각을 epoch millis 문자열로 SET EX TTL,
 * "변경 이력 없음"은 0(EPOCH) 센티넬로 저장한다. changePassword의 write-through가 모든 레플리카에 즉시
 * 반영되므로 staleness 윈도우가 없다.
 */
@Component
@ConditionalOnProperty(name = "patentflow.redis.enabled", havingValue = "true")
public class RedisPasswordChangeCache implements PasswordChangeCache {

    private static final String PREFIX = "pwd:changedAt:";

    private final StringRedisTemplate redisTemplate;

    public RedisPasswordChangeCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<Instant> get(String userId) {
        String value = redisTemplate.opsForValue().get(PREFIX + userId);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.ofEpochMilli(Long.parseLong(value)));
        } catch (NumberFormatException exception) {
            // 손상된 값은 미스로 처리해 DB 폴백 → 재캐시한다.
            return Optional.empty();
        }
    }

    @Override
    public void put(String userId, Instant passwordChangedAt) {
        long millis = passwordChangedAt == null ? NO_CHANGE.toEpochMilli() : passwordChangedAt.toEpochMilli();
        redisTemplate.opsForValue().set(PREFIX + userId, Long.toString(millis), TTL);
    }
}
