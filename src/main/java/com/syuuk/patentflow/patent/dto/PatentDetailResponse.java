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
        LocalDate annualFeeDueDate,
        String reviewReason,
        Recommendation currentRecommendation,
        BusinessOpinionDecision businessOpinionDecision,
        ExecutiveApprovalDecision executiveApprovalDecision,
        LegalActionResult legalActionResult,
        PatentSummaryResponse summary,
        AiEvaluationReportResponse aiEvaluationReport,
        FinalDecisionRecordResponse finalDecisionRecord,
        BusinessOpinionResponse businessOpinion
) {
}
