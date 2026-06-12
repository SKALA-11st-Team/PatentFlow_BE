package com.syuuk.patentflow.patent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.syuuk.patentflow.common.service.SystemSettingsService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AnnualFeeScheduleServiceTest {

    // Mockito는 Integer 반환 메서드에 null이 아닌 0을 기본 반환하므로,
    // '오버라이드 미설정(null)' 상태를 명시 스텁해 실제 기본 규칙 경로를 검증한다.
    private static SystemSettingsService settingsWithoutOverrides() {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.getCountryFeeBasisOverride(anyString())).thenReturn(null);
        when(settings.getCountryFeeInitialLumpYearsOverride(anyString())).thenReturn(null);
        when(settings.getCountryFeeCycleMonthsOverride(anyString())).thenReturn(null);
        return settings;
    }

    private final AnnualFeeScheduleService service =
            new AnnualFeeScheduleService(settingsWithoutOverrides());

    // FEE-06: KR 정밀 규칙 — 설정등록 시 1~3년차 일괄 납부, 4년차부터 등록일 기준 매년.
    @Test
    void krUsesRegistrationDateAnniversaryAfterLumpPeriod() {
        LocalDate due = service.calculateNextDueDate(
                "KR", LocalDate.of(2019, 6, 15), LocalDate.of(2020, 3, 10), null, LocalDate.of(2026, 1, 1));
        assertThat(due).isEqualTo(LocalDate.of(2026, 3, 10));
    }

    // FEE-06: 등록 후 3년이 지나지 않은 KR 특허의 최초 도래일은 4년차 납부일(등록일+3년)이다.
    @Test
    void krFirstDueDateIsFourthYearPaymentAfterRegistration() {
        LocalDate due = service.calculateNextDueDate(
                "KR", LocalDate.of(2022, 6, 15), LocalDate.of(2023, 5, 10), null, LocalDate.of(2024, 1, 1));
        assertThat(due).isEqualTo(LocalDate.of(2026, 5, 10));
    }

    // FEE-06: 일괄 구간이 끝난 해의 anniversary 당일은 그대로 도래일이다(경계).
    @Test
    void krAnniversaryOnBaseDateIsDue() {
        LocalDate due = service.calculateNextDueDate(
                "KR", null, LocalDate.of(2023, 5, 10), null, LocalDate.of(2026, 5, 10));
        assertThat(due).isEqualTo(LocalDate.of(2026, 5, 10));
    }

    // FEE-06: 미등록 KR 특허(등록일 null)는 기존처럼 출원일 기준으로 폴백한다.
    @Test
    void krFallsBackToApplicationDateWhenUnregistered() {
        LocalDate due = service.calculateNextDueDate(
                "KR", LocalDate.of(2019, 6, 15), null, null, LocalDate.of(2026, 1, 1));
        assertThat(due).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    // FEE-06: fee.rule.KR.basis 오버라이드로 출원일 기준으로 되돌릴 수 있다.
    @Test
    void krBasisCanBeOverriddenToApplicationDate() {
        SystemSettingsService settings = settingsWithoutOverrides();
        when(settings.getCountryFeeBasisOverride("KR")).thenReturn("APPLICATION_DATE");
        when(settings.getCountryFeeInitialLumpYearsOverride("KR")).thenReturn(0);
        AnnualFeeScheduleService svc = new AnnualFeeScheduleService(settings);

        LocalDate due = svc.calculateNextDueDate(
                "KR", LocalDate.of(2019, 6, 15), LocalDate.of(2020, 3, 10), null, LocalDate.of(2026, 1, 1));
        assertThat(due).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    // 일반 규칙(국가 정밀 규칙 없음): 출원일 기준 매년 도래.
    @Test
    void genericCountryUsesApplicationDateAnniversary() {
        LocalDate due = service.calculateNextDueDate(
                "JP", LocalDate.of(2019, 6, 15), LocalDate.of(2020, 3, 10), null, LocalDate.of(2026, 1, 1));
        assertThat(due).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    // 회귀 방지: 출원일이 없고 등록일만 있는 특허는 '올해 12/31'이 아니라 등록일 기준으로 계산되어야 한다.
    @Test
    void usesUsMaintenanceFeeDatesFromRegistrationDate() {
        LocalDate due = service.calculateNextDueDate(
                "US", LocalDate.of(2020, 1, 10), LocalDate.of(2021, 2, 1), null, LocalDate.of(2024, 1, 1));
        assertThat(due).isEqualTo(LocalDate.of(2024, 8, 1));

        LocalDate second = service.calculateNextDueDate(
                "US", LocalDate.of(2020, 1, 10), LocalDate.of(2021, 2, 1), null, LocalDate.of(2025, 1, 1));
        assertThat(second).isEqualTo(LocalDate.of(2028, 8, 1));
    }

    @Test
    void advancesUsMaintenanceFeeByFortyEightMonths() {
        LocalDate next = service.advanceAfterMaintenance(
                "US", LocalDate.of(2024, 8, 1), LocalDate.of(2038, 1, 10));
        assertThat(next).isEqualTo(LocalDate.of(2028, 8, 1));
    }

    // SETTINGS-11: 회차별 연장 기간이 설정된 국가는 유지 결정 회차에 해당하는 개월수로 연장한다.
    @Test
    void advancesByConfiguredExtensionForMaintainRound() {
        SystemSettingsService settings = settingsWithoutOverrides();
        when(settings.getCountryExtensionRounds("JP")).thenReturn(java.util.List.of(12, 6, 24));
        when(settings.getCountryExtensionMonthsForRound("JP", 2)).thenReturn(6);
        AnnualFeeScheduleService svc = new AnnualFeeScheduleService(settings);

        LocalDate next = svc.advanceAfterMaintenance("JP", LocalDate.of(2026, 6, 1), null, 2);
        assertThat(next).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    // SETTINGS-11: 회차 설정이 없으면 기존처럼 국가 규칙 주기(KR 12개월)로 연장한다.
    @Test
    void advancesByRuleCycleWhenNoRoundsConfigured() {
        LocalDate next = service.advanceAfterMaintenance("KR", LocalDate.of(2026, 6, 1), null, 3);
        assertThat(next).isEqualTo(LocalDate.of(2027, 6, 1));
    }

    @Test
    void fallsBackToYearEndWhenBothDatesMissing() {
        LocalDate due = service.calculateNextDueDate(
                "KR", null, null, null, LocalDate.of(2026, 1, 1));
        assertThat(due).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    // FEE-05: 과거 저장 납부일을 국가 납부 주기(KR 기본 12개월)로 굴려 오늘 이후 가장 가까운 도래일로 만든다.
    @Test
    void rollsForwardPastDueDateToNearestFuture() {
        LocalDate rolled = service.rollForwardToFuture(
                "KR", LocalDate.of(2022, 6, 15), null, LocalDate.of(2026, 1, 1));

        assertThat(rolled).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    // FEE-05: 일반 국가는 country.extension 설정값을 납부 주기로 사용한다.
    @Test
    void rollForwardUsesCountryExtensionSettingForGenericCountry() {
        SystemSettingsService settings = settingsWithoutOverrides();
        when(settings.getCountryExtensionMonths("JP")).thenReturn(12);
        AnnualFeeScheduleService svc = new AnnualFeeScheduleService(settings);

        LocalDate rolled = svc.rollForwardToFuture(
                "JP", LocalDate.of(2022, 6, 15), null, LocalDate.of(2026, 1, 1));

        assertThat(rolled).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void rollForwardKeepsFutureOrNullUnchanged() {
        LocalDate future = LocalDate.of(2027, 3, 10);
        assertThat(service.rollForwardToFuture("KR", future, null, LocalDate.of(2026, 1, 1))).isEqualTo(future);
        assertThat(service.rollForwardToFuture("KR", null, null, LocalDate.of(2026, 1, 1))).isNull();
    }

    @Test
    void rollForwardDoesNotExceedExpiration() {
        LocalDate rolled = service.rollForwardToFuture(
                "KR", LocalDate.of(2022, 6, 15), LocalDate.of(2023, 12, 31), LocalDate.of(2026, 1, 1));

        assertThat(rolled).isEqualTo(LocalDate.of(2023, 12, 31));
    }

    // FEE-06: 도래일의 연차 번호 — KR 등록일+3년 도래일은 4년차 납부분이다.
    @Test
    void annuityYearNumberCountsFromBasisDate() {
        assertThat(service.annuityYearNumber(LocalDate.of(2023, 5, 10), LocalDate.of(2026, 5, 10))).isEqualTo(4);
        assertThat(service.annuityYearNumber(LocalDate.of(2023, 5, 10), LocalDate.of(2026, 5, 9))).isEqualTo(3);
        assertThat(service.annuityYearNumber(null, LocalDate.of(2026, 5, 10))).isZero();
    }

    // FEE-06: 규칙 라벨 — KR은 일괄 납부 구간을 안내한다.
    @Test
    void paymentRuleLabelDescribesKrLumpRule() {
        assertThat(service.paymentRuleLabel("KR"))
                .isEqualTo("설정등록 시 1~3년차 일괄 납부, 4년차부터 등록일 기준 매년 납부");
        assertThat(service.paymentRuleLabel("US"))
                .isEqualTo("등록일 기준 3년 6개월, 7년 6개월, 11년 6개월 유지료");
        assertThat(service.paymentRuleLabel("JP")).isEqualTo("출원일 기준 매년 도래하는 연차료");
    }
}
