package com.syuuk.patentflow.patent.dto;

import java.time.OffsetDateTime;

/**
 * 공동출원인 합의 기록 응답/이력. 합의가 기록되지 않았으면 상세 응답에서 null 이다.
 *
 * @param status    합의 상태(PENDING/AGREED/DISAGREED).
 * @param reason    합의/불합의 사유.
 * @param decidedAt 합의 기록 시각.
 * @param decidedBy 합의를 기록한 행위자(인증 주체).
 */
public record CoApplicantConsentResponse(
        CoApplicantConsentStatus status,
        String reason,
        OffsetDateTime decidedAt,
        String decidedBy
) {
}
