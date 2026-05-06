package com.syuuk.patentflow.business.dto;

public record BusinessSubmissionChecklistScoreResponse(
        String itemId,
        int score,
        String memo
) {
}
