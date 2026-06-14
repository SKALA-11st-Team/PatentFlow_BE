package com.syuuk.patentflow.mailing.domain;

/**
 * 메일 발송 이력 상태. 기존 자유 문자열 컬럼을 타입 안전한 enum으로 고정한다.
 * - SENT: 실제 SMTP 발송 성공
 * - FAILED: 발송 시도 실패
 * - RECORDED: 발송 없이 이력만 기록(드라이런/수동 기록)
 */
public enum MailingStatus {
    SENT,
    FAILED,
    RECORDED
}
