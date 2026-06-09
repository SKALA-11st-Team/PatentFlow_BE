package com.syuuk.patentflow.mailing.service;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 OAuth2 state 저장소(멀티레플리카 공유 + 재시작 영속). state는 TTL로 두고 콜백에서 GETDEL로
 * 단발 소비하므로, 콜백이 다른 파드로 가도 검증되고 재사용(replay)도 막는다.
 */
@Component
@ConditionalOnProperty(name = "patentflow.redis.enabled", havingValue = "true")
public class RedisOAuthStateStore implements OAuthStateStore {

    private static final String KEY_PREFIX = "oauth:state:";

    private final StringRedisTemplate redisTemplate;

    public RedisOAuthStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String state, Duration ttl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + state, "1", ttl);
    }

    @Override
    public boolean consume(String state) {
        // GETDEL — 존재하면 값을 반환하며 즉시 삭제(단발). 부재면 null.
        return redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + state) != null;
    }
}
