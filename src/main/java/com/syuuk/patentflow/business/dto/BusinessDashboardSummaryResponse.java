package com.syuuk.patentflow.business.dto;

public record BusinessDashboardSummaryResponse(
        int totalAssigned,
        int pendingReview,
        int reviewed,
        int maintained,
        int abandoned
) {
}
