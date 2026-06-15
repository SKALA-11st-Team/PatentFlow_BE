package com.syuuk.patentflow.settings.dto;

/**
 * @relatedFR FR-LEGAL-24
 * I4: 국가별 연차료 규칙(FEE-06)의 유효값 — 기본 규칙 + system_settings(fee.rule.{CC}.*) 오버라이드 합성 결과.
 */
public record FeeRuleResponse(
        String country,
        String countryLabel,
        String basis,
        int initialLumpYears,
        int cycleMonths,
        String ruleLabel,
        // fe-admin-settings-3: 고정 유지료 일정(US 등)이라 기산일·일괄 연차 편집이 무시되는지 여부.
        boolean fixedSchedule
) {
}
