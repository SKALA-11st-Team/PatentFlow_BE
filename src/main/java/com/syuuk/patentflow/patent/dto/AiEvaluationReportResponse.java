package com.syuuk.patentflow.patent.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AiEvaluationReportResponse(
        String reportId,
        OffsetDateTime createdAt,
        Recommendation recommendation,
        String recommendationReason,
        Integer totalScore,
        Double averageScore,
        String finalGrade,
        String finalIndicator,
        boolean degraded,
        String failureReason,
        List<EvaluationScoreResponse> scores,
        List<String> missingInformation,
        String rawMarkdown,
        String markdownFilePath,
        // ORCH-06/AIREPORT-02: 리포트 레벨 리치 근거. 그동안 BE record 미정의로 FE까지 유실되던 필드들.
        String keyEvidence,
        List<String> judgementGrounds,
        List<String> businessCheckRequests,
        List<SourceResponse> externalSources
) {
}
