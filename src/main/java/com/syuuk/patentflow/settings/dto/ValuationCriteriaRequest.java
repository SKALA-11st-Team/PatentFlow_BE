package com.syuuk.patentflow.settings.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * 가치평가 기준 수정 요청(계약 C3 — PUT /api/v1/settings/valuation-criteria).
 *
 * @param axisWeights 축별 가중치(legal/technology/market 3개 핵심 축). 각 >0, 합계 100.
 * @param gradeCutoffs 등급 컷오프(A/B). 100 ≥ A > B ≥ 0. (A=유지 권고, B=조건부 유지, 미달=포기 검토)
 * @param businessFitOverrideThreshold 사업 연계성 유지 권고 기준점(0~100, 선택). null이면 기본값(60) 적용.
 * @param subscoreWeights legal/business_fit 축의 subscore 배점. 각 그룹 합계 100.
 */
public record ValuationCriteriaRequest(
        @NotNull Map<String, Double> axisWeights,
        @NotNull Map<String, Double> gradeCutoffs,
        Double businessFitOverrideThreshold,
        @NotNull Map<String, Map<String, Integer>> subscoreWeights
) {
}
