package com.syuuk.patentflow.patent.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AiEvaluationReportResponse(
        String reportId,
        OffsetDateTime createdAt,
        Recommendation recommendation,
        String recommendationReason,
        Integer totalScore,
        Double averageScore,
        String finalGrade,
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
        List<SourceResponse> externalSources,
        // ── 법무 편집 메타(FR-LEGAL-09) — 값 필드들은 편집 오버레이가 반영된 '유효' 값이다. ──
        boolean edited,
        String editedBy,
        OffsetDateTime editedAt,
        // 낙관적 락 토큰. 편집 PATCH의 expectedEditVersion에 그대로 돌려보낸다(미편집 시 0).
        int editVersion,
        // 편집 이후 레포트가 재생성되어 편집 기준이 낡았는지 여부(경고 표시용).
        boolean editStale,
        // 이 레포트 생성에 적용된 가치평가 기준(valuationConfig) 스냅샷. 미지원 agent 응답은 null.
        Map<String, Object> appliedCriteria,
        // xcomp-be-agent-2: Agent 계약 신호(품질 경고·근거 신뢰도). 그동안 BE record 미정의로 FE까지 유실됐다.
        List<String> warnings,
        String evidenceConfidence
) {

    /** 편집 메타가 없는(원본 그대로) 레포트용 호환 생성자 — 기존 호출부·시드 경로가 사용한다. */
    public AiEvaluationReportResponse(
            String reportId,
            OffsetDateTime createdAt,
            Recommendation recommendation,
            String recommendationReason,
            Integer totalScore,
            Double averageScore,
            String finalGrade,
            boolean degraded,
            String failureReason,
            List<EvaluationScoreResponse> scores,
            List<String> missingInformation,
            String rawMarkdown,
            String markdownFilePath,
            String keyEvidence,
            List<String> judgementGrounds,
            List<String> businessCheckRequests,
            List<SourceResponse> externalSources
    ) {
        this(reportId, createdAt, recommendation, recommendationReason, totalScore, averageScore,
                finalGrade, degraded, failureReason, scores, missingInformation,
                rawMarkdown, markdownFilePath, keyEvidence, judgementGrounds, businessCheckRequests,
                externalSources, false, null, null, 0, false, null, List.of(), null);
    }
}
