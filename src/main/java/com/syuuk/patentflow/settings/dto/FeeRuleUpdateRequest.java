/**
 * @author 유건욱
 * @date 2026-06-12
 */
package com.syuuk.patentflow.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * @relatedFR FR-LEGAL-24
 * I4: 국가별 연차료 규칙 오버라이드 수정 — null 필드는 변경하지 않고, 명시 값만 fee.rule 키에 기록한다.
 */
public record FeeRuleUpdateRequest(
        @Pattern(regexp = "APPLICATION_DATE|REGISTRATION_DATE") String basis,
        @Min(0) @Max(10) Integer initialLumpYears,
        @Min(1) @Max(120) Integer cycleMonths
) {
}
