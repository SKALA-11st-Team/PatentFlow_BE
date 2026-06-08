package com.syuuk.patentflow.patent.dto;

public record LegalDashboardSummaryResponse(
        int totalPatents,
        int pendingReview,
        int waitingBusinessResponse,
        int businessResponseReceived,
        @Deprecated int pendingLegalAction,
        int pendingFinalDecision,
        int legalActionCompleted
) {
    public LegalDashboardSummaryResponse(
            int totalPatents,
            int pendingReview,
            int waitingBusinessResponse,
            int businessResponseReceived,
            int pendingFinalDecision,
            int legalActionCompleted) {
        this(
                totalPatents,
                pendingReview,
                waitingBusinessResponse,
                businessResponseReceived,
                pendingFinalDecision,
                pendingFinalDecision,
                legalActionCompleted);
    }
}
