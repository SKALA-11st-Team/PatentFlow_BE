package com.syuuk.patentflow.common.ratelimit;

import java.time.Duration;

/**
 * P6 BE-RATELIMIT: IP+엔드포인트 단위 요청 레이트리밋 저장소.
 * InMemory(기본)/Redis(patentflow.redis.enabled=true) 구현으로 store-strategy 패턴을 따른다.
 */
public interface RateLimitStore {

    /** key에 대해 window 동안 limit회까지 허용. 한도 내면 카운트 증가 후 true, 초과면 false. */
    boolean tryConsume(String key, int limit, Duration window);
}
