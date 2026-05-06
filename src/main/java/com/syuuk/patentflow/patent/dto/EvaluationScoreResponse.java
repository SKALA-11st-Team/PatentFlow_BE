package com.syuuk.patentflow.patent.dto;

public record EvaluationScoreResponse(
        EvaluationCategory category,
        Integer score,
        String evidence
) {
}
