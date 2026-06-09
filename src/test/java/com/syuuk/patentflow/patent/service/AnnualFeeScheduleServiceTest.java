package com.syuuk.patentflow.patent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.syuuk.patentflow.common.service.SystemSettingsService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AnnualFeeScheduleServiceTest {

    private final AnnualFeeScheduleService service =
            new AnnualFeeScheduleService(mock(SystemSettingsService.class));

    @Test
    void usesApplicationDateAsBasisWhenPresent() {
        LocalDate due = service.calculateNextDueDate(
                "KR", LocalDate.of(2019, 6, 15), LocalDate.of(2020, 3, 10), null, LocalDate.of(2026, 1, 1));
        assertThat(due).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    // 회귀 방지: 출원일이 없고 등록일만 있는 특허는 '올해 12/31'이 아니라 등록일 기준으로 계산되어야 한다.
    @Test
    void fallsBackToRegistrationDateWhenApplicationDateMissing() {
        LocalDate due = service.calculateNextDueDate(
                "KR", null, LocalDate.of(2020, 3, 10), null, LocalDate.of(2026, 1, 1));
        assertThat(due).isEqualTo(LocalDate.of(2026, 3, 10));
    }

    @Test
    void fallsBackToYearEndWhenBothDatesMissing() {
        LocalDate due = service.calculateNextDueDate(
                "KR", null, null, null, LocalDate.of(2026, 1, 1));
        assertThat(due).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    // FEE-05: 과거 저장 납부일을 국가 연장 개월(12)로 굴려 오늘 이후 가장 가까운 도래일로 만든다.
    @Test
    void rollsForwardPastDueDateToNearestFuture() {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.getCountryExtensionMonths("KR")).thenReturn(12);
        AnnualFeeScheduleService svc = new AnnualFeeScheduleService(settings);

        LocalDate rolled = svc.rollForwardToFuture(
                "KR", LocalDate.of(2022, 6, 15), null, LocalDate.of(2026, 1, 1));

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
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.getCountryExtensionMonths("KR")).thenReturn(12);
        AnnualFeeScheduleService svc = new AnnualFeeScheduleService(settings);

        LocalDate rolled = svc.rollForwardToFuture(
                "KR", LocalDate.of(2022, 6, 15), LocalDate.of(2023, 12, 31), LocalDate.of(2026, 1, 1));

        assertThat(rolled).isEqualTo(LocalDate.of(2023, 12, 31));
    }
}
