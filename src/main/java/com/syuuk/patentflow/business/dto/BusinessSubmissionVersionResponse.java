package com.syuuk.patentflow.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import com.syuuk.patentflow.patent.dto.EvaluationScoreResponse;
import com.syuuk.patentflow.patent.dto.Recommendation;
import java.time.OffsetDateTime;
import java.util.List;

public record BusinessSubmissionVersionResponse(
        String submissionId,
        int version,
        BusinessOpinionDecision decision,
        String reason,
        String submittedBy,
        OffsetDateTime submittedAt,
        OffsetDateTime aiReportCreatedAt,
        Recommendation aiRecommendation,
        int aiTotalScore,
        int checklistTotal,
        List<BusinessSubmissionChecklistScoreResponse> checklistScores,
        int qualitativeScore,
        String qualitativeMemo,
        String additionalNeeds,
        String evaluatedAt,
        // fe-components-2: 제출 당시 AI 레포트 축별 점수 스냅샷(현재 레포트가 아니라 '당시' 값).
        List<EvaluationScoreResponse> snapshotScores
) {
    @JsonProperty("finalOpinion")
    public BusinessOpinionDecision finalOpinion() {
        return decision;
    }

    @JsonProperty("responses")
    public List<BusinessSubmissionChecklistScoreResponse> responses() {
        return checklistScores;
    }
}
