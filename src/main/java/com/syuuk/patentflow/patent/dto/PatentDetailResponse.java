package com.syuuk.patentflow.patent.dto;

import java.time.LocalDate;

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
        // 공동출원 합의 게이트: jointApplication=true 이면 최종 판단 전 coApplicantConsent(AGREED)가 필요.
        boolean jointApplication,
        CoApplicantConsentResponse coApplicantConsent  // 합의 미기록 시 null
) {
}
