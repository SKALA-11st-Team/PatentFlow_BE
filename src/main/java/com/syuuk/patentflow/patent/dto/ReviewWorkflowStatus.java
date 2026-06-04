package com.syuuk.patentflow.patent.dto;

/**
 * 특허 검토 워크플로우 상태.
 *
 * - NOT_IN_REVIEW            : 현재 검토 대상이 아님 (기본 상태 또는 최종 결정 완료 후 복귀)
 * - REVIEW_QUARTER_STARTED   : 분기 검토 시작 — AI 레포트 생성 대기
 * - MAIL_READY               : AI 레포트 생성 완료 — 사업부 검토 요청 메일 발송 대기
 * - WAITING_BUSINESS_RESPONSE: 사업부 검토 요청 메일 발송 완료 — 사업부 회신 대기
 * - BUSINESS_RESPONSE_RECEIVED: 사업부 의견 접수 완료 — 관리자 최종 결정 대기
 *
 * 최종 결정(legalActionResult) 후에는 NOT_IN_REVIEW 로 복귀한다.
 */
public enum ReviewWorkflowStatus {
    NOT_IN_REVIEW,
    REVIEW_QUARTER_STARTED,
    MAIL_READY,
    WAITING_BUSINESS_RESPONSE,
    BUSINESS_RESPONSE_RECEIVED
}
