package com.syuuk.patentflow.patent.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AiEvaluationReportResponse(
        String reportId,
        OffsetDateTime createdAt,
        Recommendation recommendation,
        String recommendationReason,
        Integer totalScore,
        List<EvaluationScoreResponse> scores,
        List<String> missingInformation,
        String rawMarkdown,
        String markdownFilePath
) {
}
