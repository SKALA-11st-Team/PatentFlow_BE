package com.syuuk.patentflow.auth.service;

import java.time.Duration;

/**
 * 로그인 실패/락아웃 저장소. 단일 인스턴스에선 인메모리, 멀티레플리카·재시작 환경에선 Redis(공유·영속).
 * 인메모리 구현은 재시작/롤링업데이트/레플리카 전환 시 카운터가 리셋·미공유되어 브루트포스 방어가 우회될 수 있다.
 * 임계치·잠금시간 같은 정책은 LoginAttemptService가 보유하고, 본 인터페이스는 순수 저장만 담당한다.
 */
public interface LoginAttemptStore {

    /** 실패 카운트를 1 증가시키고 현재 카운트를 반환한다. window 경과 후 카운트는 리셋된다. */
    int incrementFailures(String key, Duration window);

    /** 잠금을 lockDuration 동안 설정한다. */
    void setLock(String key, Duration lockDuration);

    /** 현재 잠금 상태인지 반환한다(만료된 잠금은 false). */
    boolean isLocked(String key);

    /** 실패 카운트와 잠금을 모두 제거한다(로그인 성공 시). */
    void reset(String key);
}
