package com.syuuk.patentflow.patent.dto;

import java.time.OffsetDateTime;

public record FinalDecisionRecordResponse(
        String decisionId,
        String reason,
        OffsetDateTime decidedAt
) {
}
