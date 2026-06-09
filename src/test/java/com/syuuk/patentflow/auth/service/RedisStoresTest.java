package com.syuuk.patentflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.syuuk.patentflow.common.ratelimit.RedisRateLimitStore;
import com.syuuk.patentflow.mailing.service.RedisOAuthStateStore;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 스토어(토큰폐기·로그인락아웃·OAuth state)를 실제 Redis로 검증한다 — SET/EX/EXISTS,
 * INCR/EXPIRE, GETDEL 실연동 확인. REDIS_HOST:REDIS_PORT(기본 localhost:6379)에 연결하며,
 * Redis가 떠 있지 않으면 assumeTrue로 자동 skip한다(예: `docker run -p 6379:6379 redis:7-alpine` 후 실행).
 */
class RedisStoresTest {

    private static LettuceConnectionFactory factory;
    private StringRedisTemplate redisTemplate;

    @BeforeAll
    static void connect() {
        String host = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        factory = new LettuceConnectionFactory(host, port);
        factory.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        redisTemplate = new StringRedisTemplate(factory);
        redisTemplate.afterPropertiesSet();
        boolean reachable;
        try {
            reachable = "PONG".equalsIgnoreCase(redisTemplate.execute((RedisConnection connection) -> connection.ping()));
        } catch (Exception exception) {
            reachable = false;
        }
        assumeTrue(reachable, "Redis 미가용 — 테스트 skip");
        redisTemplate.execute((RedisConnection connection) -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    void revokedTokenStorePersistsActiveAndIgnoresExpired() {
        RedisRevokedTokenStore store = new RedisRevokedTokenStore(redisTemplate);

        store.revoke("active-token", Instant.now().plusSeconds(60));
        assertThat(store.isRevoked("active-token")).isTrue();
        assertThat(store.isRevoked("unknown-token")).isFalse();

        // 이미 만료된 토큰은 저장하지 않는다(잔여 TTL <= 0).
        store.revoke("expired-token", Instant.now().minusSeconds(1));
        assertThat(store.isRevoked("expired-token")).isFalse();
    }

    @Test
    void loginAttemptStoreCountsFailuresLocksAndResets() {
        RedisLoginAttemptStore store = new RedisLoginAttemptStore(redisTemplate);
        Duration window = Duration.ofSeconds(300);

        assertThat(store.incrementFailures("a@x.com", window)).isEqualTo(1);
        assertThat(store.incrementFailures("a@x.com", window)).isEqualTo(2);
        assertThat(store.isLocked("a@x.com")).isFalse();

        store.setLock("a@x.com", Duration.ofSeconds(300));
        assertThat(store.isLocked("a@x.com")).isTrue();

        store.reset("a@x.com");
        assertThat(store.isLocked("a@x.com")).isFalse();
        assertThat(store.incrementFailures("a@x.com", window)).isEqualTo(1);
    }

    @Test
    void oauthStateStoreIsSingleUseViaGetDel() {
        RedisOAuthStateStore store = new RedisOAuthStateStore(redisTemplate);

        store.save("state-1", Duration.ofMinutes(10));
        assertThat(store.consume("state-1")).isTrue();   // 첫 소비 성공
        assertThat(store.consume("state-1")).isFalse();  // GETDEL 단발 — 재사용 거부
        assertThat(store.consume("never-issued")).isFalse();
    }

    @Test
    void rateLimitStoreAllowsUpToLimitThenBlocks() {
        RedisRateLimitStore store = new RedisRateLimitStore(redisTemplate);
        Duration window = Duration.ofSeconds(60);

        assertThat(store.tryConsume("1.1.1.1:/login", 2, window)).isTrue();
        assertThat(store.tryConsume("1.1.1.1:/login", 2, window)).isTrue();
        assertThat(store.tryConsume("1.1.1.1:/login", 2, window)).isFalse();   // 한도 초과
        // 다른 키는 독립 한도
        assertThat(store.tryConsume("2.2.2.2:/login", 2, window)).isTrue();
    }

    @Test
    void passwordChangeCacheStoresMillisAndNoChangeSentinel() {
        RedisPasswordChangeCache cache = new RedisPasswordChangeCache(redisTemplate);

        assertThat(cache.get("user-1")).isEmpty();   // 미스

        // 변경 시각은 epoch millis로 무손실 라운드트립(DB는 항상 withNano(0) 초 정밀도)
        Instant changedAt = Instant.ofEpochMilli(1_700_000_000_000L);
        cache.put("user-1", changedAt);
        assertThat(cache.get("user-1")).contains(changedAt);

        // null → 0(EPOCH/NO_CHANGE) 센티넬로 저장/복원
        cache.put("user-2", null);
        assertThat(cache.get("user-2")).contains(PasswordChangeCache.NO_CHANGE);
    }
}
