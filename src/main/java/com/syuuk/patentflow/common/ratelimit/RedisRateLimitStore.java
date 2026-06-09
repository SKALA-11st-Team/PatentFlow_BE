package com.syuuk.patentflow.common.ratelimit;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 레이트리밋(멀티레플리카 공유). 키별 INCR + 첫 증가에서 윈도우 EXPIRE — 슬라이딩이 아닌
 * 고정 윈도우지만 분산 환경에서 IP 한도를 공유해 레플리카 우회를 막는다.
 */
@Component
@ConditionalOnProperty(name = "patentflow.redis.enabled", havingValue = "true")
public class RedisRateLimitStore implements RateLimitStore {

    private static final String PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryConsume(String key, int limit, Duration window) {
        String redisKey = PREFIX + key;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, window);
        }
        return count == null || count <= limit;
    }
}
