package com.syuuk.patentflow.patent.dto;

import java.time.OffsetDateTime;

public record FinalDecisionRecordResponse(
        String decisionId,
        String reason,
        OffsetDateTime decidedAt,
        // REVIEW-07: 최종 판단을 기록한 행위자(인증 주체). 과거 이력 등 미상이면 null.
        String decidedBy
) {
}
