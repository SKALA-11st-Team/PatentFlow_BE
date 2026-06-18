/**
 * @author 유건욱
 * @date 2026-05-19
 */
package com.syuuk.patentflow.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @relatedFR FR-LEGAL-24
 * SETTINGS-11: 유지 결정 시 납부일 연장 기간 변경 요청. extensionMonthsByRound가 있으면
 * 회차별(1회차, 2회차, …) 연장 기간으로 저장하고 extensionMonths는 1회차 값과 동기화한다.
 * 회차 수를 넘는 유지 결정에는 마지막 회차 값이 반복 적용된다.
 */
public record CountryExtensionRequest(
        @Min(0) @Max(240) int extensionMonths,
        @Size(max = 12) List<@Min(1) @Max(240) Integer> extensionMonthsByRound
) {

    public CountryExtensionRequest(int extensionMonths) {
        this(extensionMonths, null);
    }
}
