package com.syuuk.patentflow.patent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
}
