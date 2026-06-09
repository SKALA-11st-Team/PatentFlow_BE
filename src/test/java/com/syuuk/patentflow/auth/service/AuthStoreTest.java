package com.syuuk.patentflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 인메모리 스토어(기본값) 동작 검증. Redis 구현은 동일 인터페이스를 따르지만 런타임 검증은
 * 클러스터/CI(Redis)에서 수행한다(로컬엔 Redis 미가용).
 */
class AuthStoreTest {

    @Test
    void revokedTokenStoreMarksTokenUntilExpiry() {
        InMemoryRevokedTokenStore store = new InMemoryRevokedTokenStore();
        store.revoke("token-1", Instant.now().plusSeconds(60));

        assertThat(store.isRevoked("token-1")).isTrue();
        assertThat(store.isRevoked("token-2")).isFalse();
    }

    @Test
    void revokedTokenStoreTreatsExpiredAsNotRevoked() {
        InMemoryRevokedTokenStore store = new InMemoryRevokedTokenStore();
        store.revoke("token-1", Instant.now().minusSeconds(1));

        assertThat(store.isRevoked("token-1")).isFalse();
    }

    @Test
    void loginAttemptStoreCountsFailuresAndLocks() {
        InMemoryLoginAttemptStore store = new InMemoryLoginAttemptStore();
        Duration window = Duration.ofSeconds(300);

        assertThat(store.incrementFailures("a@x.com", window)).isEqualTo(1);
        assertThat(store.incrementFailures("a@x.com", window)).isEqualTo(2);
        assertThat(store.isLocked("a@x.com")).isFalse();

        store.setLock("a@x.com", Duration.ofSeconds(300));
        assertThat(store.isLocked("a@x.com")).isTrue();
    }

    @Test
    void loginAttemptStoreResetClearsFailuresAndLock() {
        InMemoryLoginAttemptStore store = new InMemoryLoginAttemptStore();
        Duration window = Duration.ofSeconds(300);
        store.incrementFailures("a@x.com", window);
        store.setLock("a@x.com", Duration.ofSeconds(300));

        store.reset("a@x.com");

        assertThat(store.isLocked("a@x.com")).isFalse();
        assertThat(store.incrementFailures("a@x.com", window)).isEqualTo(1);
    }

    @Test
    void loginAttemptStoreExpiredLockIsNotLocked() {
        InMemoryLoginAttemptStore store = new InMemoryLoginAttemptStore();
        store.setLock("a@x.com", Duration.ofMillis(-1));

        assertThat(store.isLocked("a@x.com")).isFalse();
    }
}
