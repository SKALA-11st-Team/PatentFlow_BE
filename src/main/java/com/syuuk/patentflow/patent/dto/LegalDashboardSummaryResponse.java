package com.syuuk.patentflow.patent.dto;

public record LegalDashboardSummaryResponse(
        int totalPatents,
        // DASH-01: 이번 분기 검토 대상 수(KPI 분모 단일 출처). FE 클라이언트 재집계 대신 이 값을 우선 사용.
        int quarterlyTargetCount,
        int pendingReview,
        int mailReadySuccessCount,
        int aiReportFailedCount,
        int waitingBusinessResponse,
        int businessResponseReceived,
        @Deprecated int pendingLegalAction,
        int pendingFinalDecision,
        int legalActionCompleted
) {
    public LegalDashboardSummaryResponse(
            int totalPatents,
            int quarterlyTargetCount,
            int pendingReview,
            int mailReadySuccessCount,
            int aiReportFailedCount,
            int waitingBusinessResponse,
            int businessResponseReceived,
            int pendingFinalDecision,
            int legalActionCompleted) {
        this(
                totalPatents,
                quarterlyTargetCount,
                pendingReview,
                mailReadySuccessCount,
                aiReportFailedCount,
                waitingBusinessResponse,
                businessResponseReceived,
                pendingFinalDecision,
                pendingFinalDecision,
                legalActionCompleted);
    }
}
