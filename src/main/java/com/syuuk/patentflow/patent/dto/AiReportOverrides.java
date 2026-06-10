package com.syuuk.patentflow.patent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 법무팀이 AI 레포트에서 수정한 필드만 담는 부분 오버라이드(계약 C2).
 *
 * - 모든 필드는 선택적이며, null인 필드는 AI 원본 값을 그대로 쓴다는 뜻이다.
 * - scores는 category를 키로 한 축 단위 오버라이드다. evidenceDetails(클릭형 출처)는 AI 소유라 편집 불가.
 * - 이 객체가 ai_edit_overrides_json 컬럼에 그대로 직렬화된다(null 필드는 직렬화 제외).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiReportOverrides(
        Recommendation recommendation,
        String recommendationText,
        String keyEvidence,
        List<String> judgementGrounds,
        List<String> businessCheckRequests,
        List<ScoreOverride> scores,
        String rawMarkdown
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoreOverride(
            EvaluationCategory category,
            Integer score,
            String grade,
            String evidenceSummary
    ) {
    }

    public boolean isEmpty() {
        return recommendation == null
                && recommendationText == null
                && keyEvidence == null
                && judgementGrounds == null
                && businessCheckRequests == null
                && (scores == null || scores.isEmpty())
                && rawMarkdown == null;
    }

    /** 부분 PATCH 누적: 기존 오버라이드 위에 새 오버라이드의 non-null 필드를 덮어쓴다. */
    public AiReportOverrides mergedWith(AiReportOverrides incoming) {
        if (incoming == null) {
            return this;
        }
        return new AiReportOverrides(
                incoming.recommendation() != null ? incoming.recommendation() : recommendation,
                incoming.recommendationText() != null ? incoming.recommendationText() : recommendationText,
                incoming.keyEvidence() != null ? incoming.keyEvidence() : keyEvidence,
                incoming.judgementGrounds() != null ? incoming.judgementGrounds() : judgementGrounds,
                incoming.businessCheckRequests() != null ? incoming.businessCheckRequests() : businessCheckRequests,
                mergedScores(scores, incoming.scores()),
                incoming.rawMarkdown() != null ? incoming.rawMarkdown() : rawMarkdown);
    }

    private static List<ScoreOverride> mergedScores(List<ScoreOverride> current, List<ScoreOverride> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return current;
        }
        if (current == null || current.isEmpty()) {
            return incoming;
        }
        java.util.LinkedHashMap<EvaluationCategory, ScoreOverride> byCategory = new java.util.LinkedHashMap<>();
        for (ScoreOverride item : current) {
            byCategory.put(item.category(), item);
        }
        for (ScoreOverride item : incoming) {
            ScoreOverride existing = byCategory.get(item.category());
            if (existing == null) {
                byCategory.put(item.category(), item);
            } else {
                byCategory.put(item.category(), new ScoreOverride(
                        item.category(),
                        item.score() != null ? item.score() : existing.score(),
                        item.grade() != null ? item.grade() : existing.grade(),
                        item.evidenceSummary() != null ? item.evidenceSummary() : existing.evidenceSummary()));
            }
        }
        return List.copyOf(byCategory.values());
    }
}
