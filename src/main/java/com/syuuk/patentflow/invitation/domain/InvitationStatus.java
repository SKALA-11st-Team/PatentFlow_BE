/**
 * @author 유건욱
 * @date 2026-06-14
 */
package com.syuuk.patentflow.invitation.domain;

/**
 * @relatedFR FR-LEGAL-12, FR-LEGAL-23
 * @description 사업부 계정 초대 토큰의 상태. FE InvitationStatus 타입과 정확히 일치한다.
 *   PENDING(대기·미수락) | ACCEPTED(수락) | EXPIRED(만료) | REVOKED(회수/rotate).
 */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    EXPIRED,
    REVOKED
}
