package com.syuuk.patentflow.patent.dto;

import java.time.OffsetDateTime;

public record PatentReviewHistoryItemResponse(
        String quarterKey,
        String reviewWorkflowStatus,
        String businessOpinionDecision,
        String businessOpinionReason,
        OffsetDateTime businessOpinionSubmittedAt,
        String legalActionResult,
        String finalDecisionId,
        String finalDecisionReason,
        OffsetDateTime finalDecisionDecidedAt,
        OffsetDateTime createdAt,
        String departmentId,
        String departmentName
) {
}
