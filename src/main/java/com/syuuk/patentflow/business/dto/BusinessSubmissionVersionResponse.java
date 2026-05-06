package com.syuuk.patentflow.business.dto;

import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
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
        int qualitativeScore
) {
}
