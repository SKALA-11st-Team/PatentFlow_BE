package com.syuuk.patentflow.patent.dto;

import java.time.OffsetDateTime;

public record FinalDecisionRecordResponse(
        String decisionId,
        ExecutiveApprovalDecision decision,
        String reason,
        OffsetDateTime decidedAt
) {
}
