package com.syuuk.patentflow.patent.dto;

import java.util.List;

public record EvaluationScoreResponse(
        EvaluationCategory category,
        Integer score,
        String grade,
        String evidence,
        // ORCH-06/AIREPORT-02: 축별 세부 근거(클릭형 출처 포함). 없으면 빈 리스트.
        List<EvidenceDetailResponse> evidenceDetails
) {
}
