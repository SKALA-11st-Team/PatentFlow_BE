/**
 * @author 유건욱
 * @date 2026-06-11
 */
package com.syuuk.patentflow.patent.service;

import java.util.List;

/**
 * @relatedFR FR-LEGAL-24
 * FEE-06: 국가별 연차료 납부 규칙 디스크립터.
 * 국가마다 연차료 기산일·납부 주기·최초 일괄 납부 범위가 다르므로(KR: 설정등록 시 1~3년차 일괄,
 * US: 등록일 기준 3.5/7.5/11.5년 유지료) 규칙을 하나의 값 객체로 묶어 계산 로직이 국가 분기를
 * 직접 들고 있지 않게 한다.
 *
 * @param basis             연차 기산일 종류(APPLICATION_DATE | REGISTRATION_DATE)
 * @param initialLumpYears  설정등록 시 일괄 납부하는 연차 수(KR=3, 해당 없음=0).
 *                          일괄 구간에는 별도 납부 도래일이 생기지 않는다.
 * @param cycleMonths       이후 납부 주기(개월). roll-forward/유지 후 전진에 사용.
 * @param maintenanceMonths 등록일 기준 고정 납부 시점(개월). US 유지료처럼 매년이 아닌
 *                          고정 윈도우 국가만 값을 가진다.
 */
public record CountryAnnualFeeRule(
        String country,
        String basis,
        int initialLumpYears,
        int cycleMonths,
        List<Integer> maintenanceMonths,
        String label
) {

    public static final String BASIS_APPLICATION_DATE = "APPLICATION_DATE";
    public static final String BASIS_REGISTRATION_DATE = "REGISTRATION_DATE";

    public boolean hasMaintenanceWindows() {
        return maintenanceMonths != null && !maintenanceMonths.isEmpty();
    }

    public boolean registrationBased() {
        return BASIS_REGISTRATION_DATE.equals(basis);
    }
}
