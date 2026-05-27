package com.syuuk.patentflow.settings.service;

import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.settings.domain.QuarterSettingEntity;
import com.syuuk.patentflow.settings.domain.ReviewPeriodTemplateEntity;
import com.syuuk.patentflow.settings.repository.QuarterSettingRepository;
import com.syuuk.patentflow.settings.repository.ReviewPeriodTemplateRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 분기 자동 활성화·종료 스케줄러.
 * 관리자가 수동으로 분기를 활성화하지 않아도 매일 자정에 다음 두 작업을 수행한다:
 *  1) autoActivateQuarters — 분기 시작일 mailLeadMonths개월 전이 되면 자동 활성화
 *  2) autoEndQuarters       — 분기 endDate가 지나면 ended=true 로 자동 종료
 */
@Component
public class QuarterActivationScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuarterActivationScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SettingsService settingsService;
    private final ReviewPeriodTemplateRepository periodTemplateRepository;
    private final QuarterSettingRepository quarterSettingRepository;
    private final SystemSettingsService systemSettingsService;

    public QuarterActivationScheduler(
            SettingsService settingsService,
            ReviewPeriodTemplateRepository periodTemplateRepository,
            QuarterSettingRepository quarterSettingRepository,
            SystemSettingsService systemSettingsService) {
        this.settingsService = settingsService;
        this.periodTemplateRepository = periodTemplateRepository;
        this.quarterSettingRepository = quarterSettingRepository;
        this.systemSettingsService = systemSettingsService;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(KST);
        autoActivateQuarters(today);
        autoEndQuarters(today);
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

    // 분기 시작일 mailLeadMonths개월 전이 되면 자동 활성화하여 검토 프로세스를 시작한다.
    // 당해 연도와 내년 연도를 함께 확인해 연말 직전에 다음 연도 Q1도 미리 활성화되도록 한다.
    private void autoActivateQuarters(LocalDate today) {
        int mailLeadMonths = systemSettingsService.getMailLeadMonths();
        int currentYear = today.getYear();

        List<ReviewPeriodTemplateEntity> templates = periodTemplateRepository.findAllByOrderByPeriodNumber();

        for (int year : List.of(currentYear, currentYear + 1)) {
            for (ReviewPeriodTemplateEntity template : templates) {
                LocalDate quarterStart = LocalDate.of(year, template.getStartMonth(), template.getStartDay());
                LocalDate activationDate = quarterStart.minusMonths(mailLeadMonths);

                if (!today.isBefore(activationDate) && !quarterStart.isBefore(today)) {
                    String quarterKey = year + "-Q" + template.getPeriodNumber();
                    try {
                        settingsService.activateQuarterIfNeeded(quarterKey);
                        log.info("Auto-activated quarter: {}", quarterKey);
                    } catch (Exception e) {
                        log.warn("Failed to auto-activate quarter {}: {}", quarterKey, e.getMessage());
                    }
                }
            }
        }
    }
}
