package com.syuuk.patentflow.settings.service;

import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.patent.domain.PatentReviewHistoryEntity;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import com.syuuk.patentflow.settings.domain.QuarterSettingEntity;
import com.syuuk.patentflow.settings.domain.ReviewPeriodTemplateEntity;
import com.syuuk.patentflow.settings.repository.QuarterSettingRepository;
import com.syuuk.patentflow.settings.repository.ReviewPeriodTemplateRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 분기 자동 활성화 스케줄러.
 * - 서버 시작 시(ApplicationReadyEvent) 즉시 한 번 실행해 이미 검토 시작일이 지난 분기를 활성화한다.
 * - 매일 자정(KST)에도 실행해 새로 검토 시작일에 도달한 분기를 자동 활성화한다.
 * - 활성화 조건: 오늘 >= 검토 시작일(납부 기간 시작일 - mailLeadMonths개월)
 *               AND 오늘 <= 납부 기간 종료일
 */
@Component
public class QuarterActivationScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuarterActivationScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // 사업부에 요청됐지만 회신되지 않은 상태 — 회신기한 도달 시 지연 표시 대상
    private static final Set<ReviewWorkflowStatus> BUSINESS_RESPONSE_PENDING_STATUSES = Set.of(
            ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE
    );

    private final SettingsService settingsService;
    private final ReviewPeriodTemplateRepository periodTemplateRepository;
    private final QuarterSettingRepository quarterSettingRepository;
    private final PatentReviewHistoryRepository reviewHistoryRepository;
    private final SystemSettingsService systemSettingsService;

    public QuarterActivationScheduler(
            SettingsService settingsService,
            ReviewPeriodTemplateRepository periodTemplateRepository,
            QuarterSettingRepository quarterSettingRepository,
            PatentReviewHistoryRepository reviewHistoryRepository,
            SystemSettingsService systemSettingsService) {
        this.settingsService = settingsService;
        this.periodTemplateRepository = periodTemplateRepository;
        this.quarterSettingRepository = quarterSettingRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.systemSettingsService = systemSettingsService;
    }

    // 서버 시작 시 즉시 실행 — 검토 시작일이 이미 지난 분기를 활성화
    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        run();
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(KST);
        autoActivateQuarters(today);
        autoDelayOverduePatents(today);
        autoEndQuarters(today);
    }

    // 사업부 회신기한이 지났는데 의견이 제출되지 않은 특허에 is_delayed = true 플래그를 세팅한다.
    // review_workflow_status는 마지막 진행 단계를 유지 — 어느 단계에서 지연됐는지 파악 가능
    private void autoDelayOverduePatents(LocalDate today) {
        quarterSettingRepository.findAll().stream()
                .filter(q -> q.isActivated() && !q.isEnded())
                .forEach(q -> {
                    List<PatentReviewHistoryEntity> overdueHistory =
                            reviewHistoryRepository.findByQuarterKeyAndReviewWorkflowStatusInAndDelayedFalse(
                                    q.getQuarterKey(), BUSINESS_RESPONSE_PENDING_STATUSES)
                                    .stream()
                                    .filter(history -> history.getBusinessOpinionDecision() == null)
                                    .filter(history -> {
                                        LocalDate dueDate = history.getResponseDueDateExtendedUntil() != null
                                                ? history.getResponseDueDateExtendedUntil()
                                                : history.getResponseDueDate();
                                        if (dueDate == null) {
                                            dueDate = q.getSubmissionDeadline();
                                            history.setResponseDueDate(dueDate);
                                        }
                                        return dueDate != null && today.isAfter(dueDate);
                                    })
                                    .toList();
                    if (overdueHistory.isEmpty()) return;
                    for (PatentReviewHistoryEntity history : overdueHistory) {
                        history.setDelayed(true);  // 상태는 유지, 지연 플래그만 설정
                        reviewHistoryRepository.save(history);
                    }
                    log.info("Auto-delayed {} patents in quarter {}", overdueHistory.size(), q.getQuarterKey());
                });
    }

    // 납부 기간(endDate)이 지난 분기를 ended=true로 자동 처리.
    // 수동 종료 API를 없애고 스케줄러에 위임하여 운영 실수를 방지한다.
    private void autoEndQuarters(LocalDate today) {
        quarterSettingRepository.findAll().stream()
                .filter(q -> q.isActivated() && !q.isEnded())
                .filter(q -> q.getEndDate() != null && today.isAfter(q.getEndDate()))
                .forEach(q -> {
                    q.setEnded(true);
                    q.setEndedAt(OffsetDateTime.now(KST));
                    quarterSettingRepository.save(q);
                    log.info("Auto-ended quarter: {}", q.getQuarterKey());
                });
    }

    // 검토 시작일(납부 기간 시작일 - mailLeadMonths개월)이 되면 자동 활성화한다.
    // 당해 연도와 내년 연도를 함께 확인해 연말 직전에 다음 연도 Q1도 미리 활성화되도록 한다.
    private void autoActivateQuarters(LocalDate today) {
        int mailLeadMonths = systemSettingsService.getMailLeadMonths();
        int currentYear = today.getYear();

        List<ReviewPeriodTemplateEntity> templates = periodTemplateRepository.findAllByOrderByPeriodNumber();

        for (int year : List.of(currentYear, currentYear + 1)) {
            for (ReviewPeriodTemplateEntity template : templates) {
                LocalDate paymentPeriodStart = LocalDate.of(year, template.getStartMonth(), template.getStartDay());
                LocalDate paymentPeriodEnd = LocalDate.of(year, template.getEndMonth(), template.getEndDay());
                LocalDate reviewStartDate = paymentPeriodStart.minusMonths(mailLeadMonths);

                if (!today.isBefore(reviewStartDate) && !today.isAfter(paymentPeriodEnd)) {
                    String quarterKey = year + "-Q" + template.getPeriodNumber();
                    try {
                        if (settingsService.activateQuarterIfNeeded(quarterKey)) {
                            log.info("Auto-activated quarter: {}", quarterKey);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to auto-activate quarter {}: {}", quarterKey, e.getMessage());
                    }
                }
            }
        }
    }
}
