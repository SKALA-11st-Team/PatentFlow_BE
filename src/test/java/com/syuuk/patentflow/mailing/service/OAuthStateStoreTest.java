package com.syuuk.patentflow.mailing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 인메모리 OAuth2 state 저장소(기본값) 검증 — 단발 소비(replay 방지)·미발급/만료 거부.
 * Redis 구현은 동일 인터페이스를 따르며 런타임 검증은 클러스터/CI(Redis)에서 수행한다.
 */
class OAuthStateStoreTest {

    @Test
    void consumeIsSingleUseAndRejectsUnknown() {
        InMemoryOAuthStateStore store = new InMemoryOAuthStateStore();
        store.save("state-1", Duration.ofMinutes(10));

        assertThat(store.consume("state-1")).isTrue();   // 정상 발급 state 1회 통과
        assertThat(store.consume("state-1")).isFalse();  // 재사용(replay) 거부
        assertThat(store.consume("unknown")).isFalse();  // 미발급 state 거부
    }

    @Test
    void expiredStateIsRejected() {
        InMemoryOAuthStateStore store = new InMemoryOAuthStateStore();
        store.save("state-1", Duration.ofMillis(-1));

        assertThat(store.consume("state-1")).isFalse();
    }
}
