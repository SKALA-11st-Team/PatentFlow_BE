package com.syuuk.patentflow.patent.dto;

import java.util.List;

public record EvaluationScoreResponse(
        EvaluationCategory category,
        Integer score,
        String grade,
        String evidence,
        // ORCH-06/AIREPORT-02: 축별 세부 근거(클릭형 출처 포함). 없으면 빈 리스트.
        List<EvidenceDetailResponse> evidenceDetails,
        // AIREPORT-AXIS: 축별 상세 모달용 — 위험 요인·부족 정보·신뢰도(0~1).
        List<String> riskFactors,
        List<String> missingInformation,
        Double confidence
) {

    /** 기존 5-arg 호출부·시드 호환 생성자(추가 필드는 기본값). */
    public EvaluationScoreResponse(
            EvaluationCategory category,
            Integer score,
            String grade,
            String evidence,
            List<EvidenceDetailResponse> evidenceDetails
    ) {
        this(category, score, grade, evidence, evidenceDetails, List.of(), List.of(), null);
    }
}
