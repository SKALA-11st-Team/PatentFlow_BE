package com.syuuk.patentflow.notification.service;

import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import com.syuuk.patentflow.patent.domain.PatentReviewHistoryEntity;
import com.syuuk.patentflow.patent.dto.PatentLifecycleStatus;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @relatedFR FR-LEGAL-12, FR-BUS-01
 * F3: 검토 일정 자동 리마인드.
 * - 검토 시작일(납부일 - 메일 리드 개월) 도래: 관리자에게 "검토 시작" 알림.
 * - 납부 예정일 임박(D-30·D-7): 미회신(사업부 응답 대기) 특허를 사업부·관리자에게 에스컬레이션.
 * 매일 자정(KST) 실행, 같은 날 중복 발행은 알림 메시지 기준으로 자연 차단되지 않으므로
 * 하루 1회 실행 주기로 중복을 방지한다.
 */
@Component
public class ReviewReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReviewReminderScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int[] ESCALATION_DAYS_BEFORE_DUE = {30, 7};

    private final PatentMetadataRepository patentMetadataRepository;
    private final PatentReviewHistoryRepository reviewHistoryRepository;
    private final SystemSettingsService systemSettingsService;
    private final NotificationService notificationService;

    public ReviewReminderScheduler(
            PatentMetadataRepository patentMetadataRepository,
            PatentReviewHistoryRepository reviewHistoryRepository,
            SystemSettingsService systemSettingsService,
            NotificationService notificationService
    ) {
        this.patentMetadataRepository = patentMetadataRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.systemSettingsService = systemSettingsService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void run() {
        LocalDate today = LocalDate.now(KST);
        remindReviewStart(today);
        escalateResponseDeadline(today);
    }

    /** 검토 시작일(납부 예정일 - 메일 리드 개월)이 오늘인 활성 특허를 관리자에게 알린다. */
    private void remindReviewStart(LocalDate today) {
        int leadMonths = systemSettingsService.getMailLeadMonths();
        List<PatentMetadataEntity> due = patentMetadataRepository.findAll().stream()
                .filter(patent -> patent.getPatentStatus() == PatentLifecycleStatus.ACTIVE)
                .filter(patent -> patent.getFeeDueDate() != null
                        && patent.getFeeDueDate().minusMonths(leadMonths).equals(today))
                .toList();
        if (due.isEmpty()) {
            return;
        }
        notificationService.addNotification(
                "연차료 검토 시작 알림",
                "오늘 검토를 시작할 특허가 %d건 있습니다(납부 예정 %d개월 전).".formatted(due.size(), leadMonths),
                "ADMIN",
                "/admin/review-targets");
        log.info("검토 시작 리마인드 발행 — {}건", due.size());
    }

    /**
     * 납부 예정일 D-30·D-7 시점에 아직 미회신(사업부 응답 대기)인 특허를 에스컬레이션한다.
     * 정확히 해당 D-day에만 발행해 매일 반복 알림(스팸)을 막는다.
     */
    private void escalateResponseDeadline(LocalDate today) {
        for (int daysBefore : ESCALATION_DAYS_BEFORE_DUE) {
            LocalDate dueTarget = today.plusDays(daysBefore);
            List<PatentReviewHistoryEntity> waiting = reviewHistoryRepository.findAll().stream()
                    .filter(history -> history.getReviewWorkflowStatus() == ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE)
                    .filter(history -> history.getAnnualFeeDueDate() != null
                            && history.getAnnualFeeDueDate().equals(dueTarget))
                    .toList();
            if (waiting.isEmpty()) {
                continue;
            }
            notificationService.addNotification(
                    "검토 회신 요청 (납부 D-%d)".formatted(daysBefore),
                    "납부 예정일이 %d일 남은 미회신 특허가 %d건 있습니다. 의견을 제출해 주세요.".formatted(daysBefore, waiting.size()),
                    "BUSINESS",
                    "/business/dashboard");
            notificationService.addNotification(
                    "사업부 회신 지연 주의 (납부 D-%d)".formatted(daysBefore),
                    "납부 D-%d 미회신 특허 %d건 — 후속 조치를 검토해 주세요.".formatted(daysBefore, waiting.size()),
                    "ADMIN",
                    "/admin/review-targets");
            log.info("회신 에스컬레이션 발행 — D-{} {}건", daysBefore, waiting.size());
        }
    }
}
