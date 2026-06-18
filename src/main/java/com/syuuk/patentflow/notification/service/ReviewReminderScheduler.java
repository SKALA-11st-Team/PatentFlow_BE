/**
 * @author 유건욱
 * @date 2026-06-12
 */
package com.syuuk.patentflow.notification.service;

import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.notification.domain.NotificationEntity;
import com.syuuk.patentflow.notification.repository.NotificationRepository;
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
 * 매일 자정(KST) 실행. 멀티 레플리카(EKS) 환경에서 모든 인스턴스가 동시에 run()을 실행하므로
 * 하루 1회 실행 주기만으로는 중복을 막지 못한다. 발행 시 "오늘(KST)·동일 제목·동일 대상 역할"의
 * 알림이 이미 존재하면 건너뛰는 멱등 발행으로 인스턴스 간 중복을 방지한다.
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
    private final NotificationRepository notificationRepository;
    private final SchedulerLockService schedulerLockService;

    public ReviewReminderScheduler(
            PatentMetadataRepository patentMetadataRepository,
            PatentReviewHistoryRepository reviewHistoryRepository,
            SystemSettingsService systemSettingsService,
            NotificationService notificationService,
            NotificationRepository notificationRepository,
            SchedulerLockService schedulerLockService
    ) {
        this.patentMetadataRepository = patentMetadataRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.systemSettingsService = systemSettingsService;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.schedulerLockService = schedulerLockService;
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void run() {
        LocalDate today = LocalDate.now(KST);
        // 멀티 replica 중복 발행 방지: 오늘자 실행을 DB 락으로 단일 인스턴스만 수행한다.
        if (!schedulerLockService.tryClaim("review-reminder", today)) {
            log.info("[ReviewReminder] another instance already claimed today's run ({}); skipping.", today);
            return;
        }
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
        publishOnce(
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
            publishOnce(
                    "검토 회신 요청 (납부 D-%d)".formatted(daysBefore),
                    "납부 예정일이 %d일 남은 미회신 특허가 %d건 있습니다. 의견을 제출해 주세요.".formatted(daysBefore, waiting.size()),
                    "BUSINESS",
                    "/business/dashboard");
            publishOnce(
                    "사업부 회신 지연 주의 (납부 D-%d)".formatted(daysBefore),
                    "납부 D-%d 미회신 특허 %d건 — 후속 조치를 검토해 주세요.".formatted(daysBefore, waiting.size()),
                    "ADMIN",
                    "/admin/review-targets");
            log.info("회신 에스컬레이션 발행 — D-{} {}건", daysBefore, waiting.size());
        }
    }

    /**
     * 멱등 발행 — 오늘(KST) 동일 제목·동일 대상 역할의 알림이 이미 있으면 발행을 건너뛴다.
     * EKS 멀티 레플리카에서 동일 스케줄 잡이 여러 인스턴스에 동시 실행돼도 사용자에게
     * 같은 알림이 중복으로 쌓이지 않도록 보장한다.
     */
    private void publishOnce(String title, String message, String targetRole, String link) {
        if (alreadyPublishedToday(title, targetRole)) {
            log.debug("중복 발행 건너뜀 — 제목='{}', 역할={}", title, targetRole);
            return;
        }
        notificationService.addNotification(title, message, targetRole, link);
    }

    private boolean alreadyPublishedToday(String title, String targetRole) {
        LocalDate today = LocalDate.now(KST);
        return notificationRepository.findByTargetRoleInOrderByCreatedAtDesc(List.of(targetRole)).stream()
                .filter(notification -> title.equals(notification.getTitle()))
                .map(NotificationEntity::getCreatedAt)
                .anyMatch(createdAt -> createdAt != null
                        && createdAt.atZoneSameInstant(KST).toLocalDate().equals(today));
    }
}
