package com.syuuk.patentflow.patent.dto;

/**
 * 공동출원 특허의 공동출원인 합의 상태.
 *
 * <p>공동출원 특허는 연차료 유지/포기 최종 판단 전에 공동출원인과 합의가 필요하다.
 * AGREED 인 경우에만 최종 판단을 진행할 수 있다(게이트).
 */
public enum CoApplicantConsentStatus {
    PENDING,    // 합의 대기(미기록)
    AGREED,     // 합의 완료 — 최종 판단 진행 가능
    DISAGREED   // 합의 불성립 — 최종 판단 차단
}
