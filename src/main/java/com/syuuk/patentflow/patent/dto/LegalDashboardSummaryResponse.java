package com.syuuk.patentflow.patent.dto;

public record LegalDashboardSummaryResponse(
        int totalPatents,
        int pendingReview,
        int waitingBusinessResponse,
        int businessResponseReceived,
        int pendingLegalAction
) {
}
