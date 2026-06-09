package com.syuuk.patentflow.mailing.service;

import java.time.Duration;

/**
 * OAuth2 CSRF 방어용 state 저장소. authorize 요청 시 발급한 state를 저장하고, 콜백에서 단발(single-use)로
 * 소비·검증한다. 단일 인스턴스에선 인메모리, 멀티레플리카(콜백이 다른 파드로 갈 수 있음)에선 Redis를 쓴다.
 */
public interface OAuthStateStore {

    void save(String state, Duration ttl);

    /** state가 존재하면 제거하고 true(단발). 부재/만료면 false. */
    boolean consume(String state);
}
