package com.syuuk.patentflow.patent.dto;

import java.time.LocalDate;

public record PatentListItemResponse(
        String patentId,
        String managementNumber,
        String applicationNumber,
        String registrationNumber,
        String title,
        String draftTitle,
        String businessArea,
        String technologyArea,
        String productName,
        String country,
        String coApplicants,
        LocalDate applicationDate,
        LocalDate registrationDate,
        LocalDate expectedExpirationDate,
        String departmentId,
        String departmentName,
        PatentLifecycleStatus lifecycleStatus,
        ReviewWorkflowStatus reviewWorkflowStatus,
        LocalDate feeDueDate,
        String reviewReason,
        Recommendation currentRecommendation,
        BusinessOpinionDecision businessOpinionDecision,
        LegalActionResult legalActionResult,
        String originalPatentUrl,
        boolean inReview,       // 현재 분기 검토 대상 여부 (patents.is_in_review)
        String currentQuarterKey, // 현재 속한 검토 분기 키, 검토 아님이면 null
        boolean isDelayed,      // 회신기한 또는 납부기간 기준 지연 — review_workflow_status는 마지막 단계 유지
        LocalDate responseDueDate,
        LocalDate responseDueDateExtendedUntil,
        java.time.OffsetDateTime urgentRequestedAt
) {
}
