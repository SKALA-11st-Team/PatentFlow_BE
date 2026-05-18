package com.syuuk.patentflow.patent.dto;

public record FinalDecisionResponse(
        String patentId,
        FinalDecisionRecordResponse finalDecisionRecord,
        LegalActionResult legalActionResult,
        ReviewWorkflowStatus reviewWorkflowStatus
) {
}
