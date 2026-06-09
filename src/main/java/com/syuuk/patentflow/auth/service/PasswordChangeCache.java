package com.syuuk.patentflow.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * AUTH-08: 비밀번호 변경 시각 캐시. JWT 필터가 매 요청 DB를 조회해 토큰 staleness를 검증하던 것을 캐시로 줄인다.
 * 비밀번호 변경은 changePassword에서 write-through하므로(공유 Redis면 모든 레플리카 즉시 반영) 정합성이 유지된다.
 * 값은 변경 시각(epoch millis), "변경 이력 없음"은 EPOCH(millis 0) 센티넬로 표현한다. 캐시 미스는 호출부가 DB로 폴백한다.
 */
public interface PasswordChangeCache {

    Duration TTL = Duration.ofMinutes(5);
    Instant NO_CHANGE = Instant.EPOCH;

    /** 캐시된 비밀번호 변경 시각(또는 NO_CHANGE). 미스면 empty. */
    Optional<Instant> get(String userId);

    /** 비밀번호 변경 시각을 캐시한다. null이면 변경 이력 없음(NO_CHANGE)으로 저장한다. */
    void put(String userId, Instant passwordChangedAt);
}
