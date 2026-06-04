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
        boolean inReview,           // 현재 분기 검토 대상 여부 (patents.is_in_review 반영)
        String currentQuarterKey    // 현재 속한 검토 분기 키 (예: "2026-Q2"), 검토 아님이면 null
) {
}
