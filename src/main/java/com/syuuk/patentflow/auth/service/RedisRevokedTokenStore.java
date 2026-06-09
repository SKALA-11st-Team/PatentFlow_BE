package com.syuuk.patentflow.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 폐기 저장소(멀티레플리카 공유 + 재시작 영속). 키는 토큰 SHA-256 해시, 값은 "1",
 * TTL은 토큰 잔여 만료시간으로 둬 만료된 토큰 키는 Redis가 자동 정리한다.
 * (운영 전제: Redis는 PVC AOF 영속으로 새벽 재시작을 견딘다.)
 */
@Component
@ConditionalOnProperty(name = "patentflow.redis.enabled", havingValue = "true")
public class RedisRevokedTokenStore implements RevokedTokenStore {

    private static final String KEY_PREFIX = "revoked:jwt:";

    private final StringRedisTemplate redisTemplate;

    public RedisRevokedTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void revoke(String token, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        // 이미 만료된 토큰은 저장할 필요가 없다(자연 만료로 어차피 무효).
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(key(token), "1", ttl);
    }

    @Override
    public boolean isRevoked(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(token)));
    }

    private String key(String token) {
        return KEY_PREFIX + sha256(token);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
