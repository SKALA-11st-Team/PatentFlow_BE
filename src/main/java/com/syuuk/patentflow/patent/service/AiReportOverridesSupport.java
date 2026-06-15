package com.syuuk.patentflow.patent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.patent.domain.PatentReviewHistoryEntity;
import com.syuuk.patentflow.patent.dto.AiEvaluationReportResponse;
import com.syuuk.patentflow.patent.dto.AiReportOverrides;
import com.syuuk.patentflow.patent.dto.EvaluationScoreResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 원본 레포트 위에 법무 편집 오버라이드를 오버레이해 '유효 레포트'를 만드는 순수 유틸.
 *
 * AI 원본(ai_* 컬럼)과 편집(ai_edit_overrides_json)은 분리 저장되고(FR-LEGAL-09),
 * 조회 경로에서만 이 클래스로 합성된다 — 합성 결과가 다시 ai_* 컬럼에 영속되어서는 안 된다.
 */
final class AiReportOverridesSupport {

    private AiReportOverridesSupport() {
    }

    static AiReportOverrides readOverrides(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AiReportOverrides.class);
        } catch (Exception exception) {
            // 손상된 오버라이드 JSON 때문에 레포트 조회 전체가 죽지 않도록 원본으로 폴백한다.
            return null;
        }
    }

    static AiEvaluationReportResponse applyOverrides(
            AiEvaluationReportResponse original,
            AiReportOverrides overrides,
            PatentReviewHistoryEntity state
    ) {
        int editVersion = state.getAiEditVersion() == null ? 0 : state.getAiEditVersion();
        if (overrides == null || overrides.isEmpty()) {
            // 편집이 없어도 editVersion(낙관적 락 토큰)은 노출해 FE가 첫 편집 요청에 쓸 수 있게 한다.
            return withEditMeta(original, false, null, null, editVersion, false);
        }
        boolean stale = state.getAiEditBaseReportId() != null
                && !state.getAiEditBaseReportId().equals(state.getAiReportId());
        AiEvaluationReportResponse merged = new AiEvaluationReportResponse(
                original.reportId(),
                original.createdAt(),
                overrides.recommendation() != null ? overrides.recommendation() : original.recommendation(),
                overrides.recommendationText() != null ? overrides.recommendationText() : original.recommendationReason(),
                original.totalScore(),
                original.averageScore(),
                original.finalGrade(),
                original.degraded(),
                original.failureReason(),
                mergeScores(original.scores(), overrides.scores()),
                original.missingInformation(),
                overrides.rawMarkdown() != null ? overrides.rawMarkdown() : original.rawMarkdown(),
                original.markdownFilePath(),
                overrides.keyEvidence() != null ? overrides.keyEvidence() : original.keyEvidence(),
                overrides.judgementGrounds() != null ? overrides.judgementGrounds() : original.judgementGrounds(),
                overrides.businessCheckRequests() != null
                        ? overrides.businessCheckRequests() : original.businessCheckRequests(),
                original.externalSources(),
                original.edited(), original.editedBy(), original.editedAt(),
                original.editVersion(), original.editStale(), original.appliedCriteria(),
                original.warnings(), original.evidenceConfidence(),
                original.summaryBrief(), original.reportSections());
        return withEditMeta(merged, true, state.getAiEditedBy(), state, editVersion, stale);
    }

    private static AiEvaluationReportResponse withEditMeta(
            AiEvaluationReportResponse report, boolean edited, String editedBy,
            PatentReviewHistoryEntity state, int editVersion, boolean stale
    ) {
        return new AiEvaluationReportResponse(
                report.reportId(), report.createdAt(), report.recommendation(), report.recommendationReason(),
                report.totalScore(), report.averageScore(), report.finalGrade(),
                report.degraded(), report.failureReason(), report.scores(), report.missingInformation(),
                report.rawMarkdown(), report.markdownFilePath(), report.keyEvidence(), report.judgementGrounds(),
                report.businessCheckRequests(), report.externalSources(),
                edited, editedBy, state == null ? null : state.getAiEditedAt(), editVersion, stale,
                report.appliedCriteria(),
                report.warnings(), report.evidenceConfidence(),
                report.summaryBrief(), report.reportSections());
    }

    static AiEvaluationReportResponse withAppliedCriteria(
            AiEvaluationReportResponse report, Map<String, Object> appliedCriteria,
            List<String> warnings, String evidenceConfidence,
            Map<String, Object> summaryBrief, Map<String, String> reportSections
    ) {
        return new AiEvaluationReportResponse(
                report.reportId(), report.createdAt(), report.recommendation(), report.recommendationReason(),
                report.totalScore(), report.averageScore(), report.finalGrade(),
                report.degraded(), report.failureReason(), report.scores(), report.missingInformation(),
                report.rawMarkdown(), report.markdownFilePath(), report.keyEvidence(), report.judgementGrounds(),
                report.businessCheckRequests(), report.externalSources(),
                report.edited(), report.editedBy(), report.editedAt(), report.editVersion(), report.editStale(),
                appliedCriteria != null ? appliedCriteria : report.appliedCriteria(),
                warnings != null ? warnings : report.warnings(),
                evidenceConfidence != null ? evidenceConfidence : report.evidenceConfidence(),
                summaryBrief != null ? summaryBrief : report.summaryBrief(),
                reportSections != null ? reportSections : report.reportSections());
    }

    private static List<EvaluationScoreResponse> mergeScores(
            List<EvaluationScoreResponse> original,
            List<AiReportOverrides.ScoreOverride> overrides
    ) {
        if (overrides == null || overrides.isEmpty() || original == null || original.isEmpty()) {
            return original;
        }
        Map<com.syuuk.patentflow.patent.dto.EvaluationCategory, AiReportOverrides.ScoreOverride> byCategory =
                new LinkedHashMap<>();
        for (AiReportOverrides.ScoreOverride override : overrides) {
            if (override.category() != null) {
                byCategory.put(override.category(), override);
            }
        }
        return original.stream()
                .map(score -> {
                    AiReportOverrides.ScoreOverride override = byCategory.get(score.category());
                    if (override == null) {
                        return score;
                    }
                    // evidenceDetails(클릭형 출처)는 AI 소유 — 항상 원본 유지.
                    return new EvaluationScoreResponse(
                            score.category(),
                            override.score() != null ? override.score() : score.score(),
                            override.grade() != null ? override.grade() : score.grade(),
                            override.evidenceSummary() != null ? override.evidenceSummary() : score.evidence(),
                            score.evidenceDetails());
                })
                .toList();
    }
}
