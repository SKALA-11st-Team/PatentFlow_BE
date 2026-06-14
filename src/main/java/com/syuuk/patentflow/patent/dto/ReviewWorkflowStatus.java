package com.syuuk.patentflow.patent.dto;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 특허 검토 워크플로우 상태.
 *
 * - NOT_IN_REVIEW            : 현재 검토 대상이 아님 (기본 상태 또는 최종 결정 완료 후 복귀)
 * - REVIEW_QUARTER_STARTED   : 분기 검토 시작 — AI 레포트 생성 대기
 * - MAIL_READY               : AI 레포트 생성 완료 — 사업부 검토 요청 메일 발송 대기
 * - WAITING_BUSINESS_RESPONSE: 사업부 검토 요청 메일 발송 완료 — 사업부 회신 대기
 * - BUSINESS_RESPONSE_RECEIVED: 사업부 의견 접수 완료 — 관리자 최종 결정 대기
 * - LEGAL_ACTION_RECORDED     : 법무 처리 결과 기록 완료 — 분기 검토 종료(처리 완료)
 *
 * 최종 결정(legalActionResult) 기록 시 LEGAL_ACTION_RECORDED 로 전이한다.
 * '처리 완료'를 '미검토(NOT_IN_REVIEW)'와 구분하기 위함이며, 최종 결정 취소 시
 * BUSINESS_RESPONSE_RECEIVED 로 복귀한다.
 */
public enum ReviewWorkflowStatus {
    NOT_IN_REVIEW,
    REVIEW_QUARTER_STARTED,
    MAIL_READY,
    WAITING_BUSINESS_RESPONSE,
    BUSINESS_RESPONSE_RECEIVED,
    LEGAL_ACTION_RECORDED;

    // REVIEW-09: 허용 워크플로우 전이의 단일 정본(중앙 전이표). 분산된 가드가 산발적으로 상태를 강제하던 것을
    // 한 곳에서 검증 가능하게 한다. 정상 순환: 분기시작 → 메일대기 → 회신대기 → 의견접수 → 처리완료(LEGAL_ACTION_RECORDED).
    // 최종 결정 취소 시 처리완료 → 의견접수 로 복귀한다.
    private static final Map<ReviewWorkflowStatus, Set<ReviewWorkflowStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(ReviewWorkflowStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(NOT_IN_REVIEW, EnumSet.of(REVIEW_QUARTER_STARTED));
        ALLOWED_TRANSITIONS.put(REVIEW_QUARTER_STARTED, EnumSet.of(MAIL_READY));
        ALLOWED_TRANSITIONS.put(MAIL_READY, EnumSet.of(WAITING_BUSINESS_RESPONSE));
        ALLOWED_TRANSITIONS.put(WAITING_BUSINESS_RESPONSE, EnumSet.of(BUSINESS_RESPONSE_RECEIVED));
        ALLOWED_TRANSITIONS.put(BUSINESS_RESPONSE_RECEIVED, EnumSet.of(LEGAL_ACTION_RECORDED));
        ALLOWED_TRANSITIONS.put(LEGAL_ACTION_RECORDED, EnumSet.of(BUSINESS_RESPONSE_RECEIVED));
    }

    /** 현재 상태에서 target 으로의 전이가 허용되는지. 동일 상태 재설정(멱등)은 허용한다. */
    public boolean canTransitionTo(ReviewWorkflowStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(ReviewWorkflowStatus.class)).contains(target);
    }
}
