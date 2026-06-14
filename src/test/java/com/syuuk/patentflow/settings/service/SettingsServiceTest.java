package com.syuuk.patentflow.settings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.patent.service.AiReportBatchService;
import com.syuuk.patentflow.patent.service.PatentReviewService;
import com.syuuk.patentflow.settings.domain.QuarterSettingEntity;
import com.syuuk.patentflow.settings.domain.ReviewPeriodTemplateEntity;
import com.syuuk.patentflow.settings.dto.QuarterSettingRequest;
import com.syuuk.patentflow.settings.repository.QuarterSettingRepository;
import com.syuuk.patentflow.settings.repository.ReviewPeriodTemplateRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private QuarterSettingRepository quarterSettingRepository;

    @Mock
    private ReviewPeriodTemplateRepository periodTemplateRepository;

    @Mock
    private PatentReviewService patentReviewService;

    @Mock
    private AiReportBatchService aiReportBatchService;

    @Mock
    private SystemSettingsService systemSettingsService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private SettingsService service;

    @BeforeEach
    void setUp() {
        // SETTINGS-09: 생성자가 더 이상 템플릿을 시드하지 않으므로 시드 stub 불필요.
        service = new SettingsService(
                quarterSettingRepository,
                periodTemplateRepository,
                patentReviewService,
                aiReportBatchService,
                systemSettingsService,
                eventPublisher);
    }

    @Test
    void activateQuarterIsIdempotentAndDoesNotStartDuplicateAiBatch() {
        QuarterSettingEntity active = quarter("2026-Q3", 2026, 3, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30));
        active.setActivated(true);

        when(quarterSettingRepository.findByIdForUpdate("2026-Q3")).thenReturn(Optional.of(active));
        when(patentReviewService.getAllPatents()).thenReturn(List.of());

        service.activateQuarter("2026-Q3");

        verify(patentReviewService, never()).createQuarterReviewTargets(any(), any(), any());
        verify(aiReportBatchService, never()).generateReportsForQuarter(any(), any());
    }

    @Test
    void activateQuarterRejectsAnotherActiveQuarter() {
        QuarterSettingEntity target = quarter("2026-Q4", 2026, 4, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31));
        QuarterSettingEntity alreadyActive = quarter("2026-Q3", 2026, 3, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30));
        alreadyActive.setActivated(true);

        when(quarterSettingRepository.findByIdForUpdate("2026-Q4")).thenReturn(Optional.of(target));
        when(quarterSettingRepository.findActiveForUpdate()).thenReturn(List.of(alreadyActive));

        assertThatThrownBy(() -> service.activateQuarter("2026-Q4"))
                .isInstanceOf(PatentFlowException.class)
                .hasMessageContaining("이미 활성화된 분기");

        verify(patentReviewService, never()).createQuarterReviewTargets(any(), any(), any());
        verify(aiReportBatchService, never()).generateReportsForQuarter(any(), any());
    }

    @Test
    void activateQuarterRejectsQuarterNumberOutOfRange() {
        // 회귀: 분기 번호는 1~4로만 허용(Q5/Q0 거부).
        assertThatThrownBy(() -> service.activateQuarter("2026-Q5"))
                .isInstanceOf(PatentFlowException.class)
                .hasMessageContaining("Q1부터 Q4");
        assertThatThrownBy(() -> service.activateQuarter("2026-Q0"))
                .isInstanceOf(PatentFlowException.class)
                .hasMessageContaining("Q1부터 Q4");
    }

    @Test
    void activateQuarterRejectsOutOfRangeYear() {
        // 회귀: 연도 무경계 갭 보강 — 비정상 연도(과대/과소) 거부.
        assertThatThrownBy(() -> service.activateQuarter("99999-Q1"))
                .isInstanceOf(PatentFlowException.class)
                .hasMessageContaining("유효하지 않은 분기 연도");
        assertThatThrownBy(() -> service.activateQuarter("1900-Q1"))
                .isInstanceOf(PatentFlowException.class)
                .hasMessageContaining("유효하지 않은 분기 연도");
    }

    @Test
    void updateQuarterSettingRejectsResponseDueDateAfterQuarterEnd() {
        QuarterSettingEntity quarter = quarter("2026-Q3", 2026, 3, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30));

        when(quarterSettingRepository.findById("2026-Q3")).thenReturn(Optional.of(quarter));
        when(systemSettingsService.getMailLeadMonths()).thenReturn(2);

        assertThatThrownBy(() -> service.updateQuarterSetting(
                "2026-Q3",
                new QuarterSettingRequest(null, null, null, LocalDate.of(2026, 10, 1))))
                .isInstanceOf(PatentFlowException.class)
                .hasMessageContaining("분기 종료일");
    }

    @Test
    void activateQuarterClampsAutoDueDateToQuarterEndInsteadOfRollingBackActivation() {
        // 회귀(be-settings-2): 분기 마지막 달에 처음 활성화 자격이 생기면 자동 계산 회신 기한이
        // 분기 종료일을 넘어 전체 활성화 트랜잭션이 롤백되어 검토 대상이 영구 미생성되던 결함.
        // 종료일이 오늘인 분기(=자동 계산 회신 기한이 종료일을 초과)를 활성화해도 예외 없이
        // 진행되고, 회신 기한은 종료일로 clamp되며 검토 대상 생성/AI 배치가 수행되어야 한다.
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        LocalDate startDate = LocalDate.of(today.getYear(), 1, 1);
        QuarterSettingEntity target = quarter(
                today.getYear() + "-Q1", today.getYear(), 1, startDate, today);

        when(quarterSettingRepository.findByIdForUpdate(target.getQuarterKey()))
                .thenReturn(Optional.of(target));
        when(quarterSettingRepository.findActiveForUpdate()).thenReturn(List.of());
        when(systemSettingsService.getResponseDeadlineMonths()).thenReturn(1);
        when(systemSettingsService.getResponseDeadlineDays()).thenReturn(0);
        lenient().when(systemSettingsService.getMailLeadMonths()).thenReturn(2);
        when(patentReviewService.createQuarterReviewTargets(eq(target.getQuarterKey()), any(), any()))
                .thenReturn(List.of("KR1020230000001"));

        assertThatCode(() -> service.activateQuarter(target.getQuarterKey())).doesNotThrowAnyException();

        assertThat(target.isActivated()).isTrue();
        assertThat(target.getSubmissionDeadline()).isEqualTo(today);
        verify(patentReviewService).createQuarterReviewTargets(eq(target.getQuarterKey()), any(), any());
        verify(aiReportBatchService).generateReportsForQuarter(anyList(), eq(target.getQuarterKey()));
    }

    private QuarterSettingEntity quarter(
            String quarterKey,
            int year,
            int quarterNumber,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return new QuarterSettingEntity(quarterKey, year, quarterNumber, startDate, endDate);
    }
}
