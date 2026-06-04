package com.syuuk.patentflow.patent.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PatentDetailResponse(
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
        PatentSummaryResponse summary,
        AiEvaluationReportResponse aiEvaluationReport,
        FinalDecisionRecordResponse finalDecisionRecord,
        BusinessOpinionResponse businessOpinion,
        boolean inReview,  // 현재 분기 검토 대상 여부 (patents.is_in_review 반영)
        boolean isDelayed,  // 납부기간 시작일까지 검토 미완료 여부
        LocalDate responseDueDate, // 사업부 회신기한
        LocalDate responseDueDateExtendedUntil, // 기한 연장일
        OffsetDateTime urgentRequestedAt, // 긴급 요청 시각
        String currentQuarterKey // 현재 검토 분기 키
) {
}
