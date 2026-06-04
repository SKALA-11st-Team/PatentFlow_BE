package com.syuuk.patentflow.patent.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PatentReviewHistoryItemResponse(
        String quarterKey,
        String reviewWorkflowStatus,  // 마지막 진행 단계 (지연된 경우에도 단계 유지)
        boolean isDelayed,            // 납부기간 시작일까지 미완료 여부
        String businessOpinionDecision,
        String businessOpinionReason,
        OffsetDateTime businessOpinionSubmittedAt,
        String legalActionResult,
        String finalDecisionId,
        String finalDecisionReason,
        OffsetDateTime finalDecisionDecidedAt,
        LocalDate responseDueDate,
        LocalDate responseDueDateExtendedUntil,
        OffsetDateTime urgentRequestedAt,
        OffsetDateTime createdAt,
        String departmentId,
        String departmentName
) {
}
