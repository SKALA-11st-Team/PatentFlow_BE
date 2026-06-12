package com.syuuk.patentflow.common.dto;

import java.util.List;

/**
 * SETTINGS-11: extensionMonthsByRound는 유지 결정 회차별 연장 기간(1회차부터 순서대로).
 * 회차 설정이 없으면 [extensionMonths] 한 개짜리 목록으로 응답한다.
 */
public record CountryExtensionResponse(
        String country,
        String label,
        int extensionMonths,
        List<Integer> extensionMonthsByRound
) {}
