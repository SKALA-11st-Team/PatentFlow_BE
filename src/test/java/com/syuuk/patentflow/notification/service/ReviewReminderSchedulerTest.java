package com.syuuk.patentflow.notification.service;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.notification.domain.NotificationEntity;
import com.syuuk.patentflow.notification.repository.NotificationRepository;
import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import com.syuuk.patentflow.patent.dto.PatentLifecycleStatus;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * NOTI-13: ReviewReminderScheduler 회귀 가드.
 * - be-notification-1: run()이 쓰기 트랜잭션으로 동작해 알림 발행 경로가 실제 실행되는지(addNotification 호출).
 * - be-notification-2: 같은 날 동일 제목·역할 알림이 이미 있으면 멱등 발행으로 중복을 막는지(멀티 레플리카 대비).
 */
class ReviewReminderSchedulerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PatentMetadataRepository patentMetadataRepository = mock(PatentMetadataRepository.class);
    private final PatentReviewHistoryRepository reviewHistoryRepository = mock(PatentReviewHistoryRepository.class);
    private final SystemSettingsService systemSettingsService = mock(SystemSettingsService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final SchedulerLockService schedulerLockService = mock(SchedulerLockService.class);

    private final ReviewReminderScheduler scheduler = new ReviewReminderScheduler(
            patentMetadataRepository,
            reviewHistoryRepository,
            systemSettingsService,
            notificationService,
            notificationRepository,
            schedulerLockService);

    @org.junit.jupiter.api.BeforeEach
    void allowSchedulerLock() {
        when(schedulerLockService.tryClaim(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
    }

    @Test
    void publishesReviewStartReminderWhenPatentIsDueToday() {
        int leadMonths = 2;
        when(systemSettingsService.getMailLeadMonths()).thenReturn(leadMonths);
        // 검토 시작일(= 납부 예정일 - 리드 개월)이 오늘인 활성 특허 1건.
        LocalDate feeDueDate = LocalDate.now(KST).plusMonths(leadMonths);
        when(patentMetadataRepository.findAll()).thenReturn(List.of(activePatent("PAT-1", feeDueDate)));
        when(reviewHistoryRepository.findAll()).thenReturn(List.of());
        // 오늘 발행 이력 없음 → 발행되어야 한다.
        when(notificationRepository.findByTargetRoleInOrderByCreatedAtDesc(anyCollection()))
                .thenReturn(List.of());

        scheduler.run();

        verify(notificationService, times(1)).addNotification(
                eq("연차료 검토 시작 알림"), anyString(), eq("ADMIN"), anyString());
    }

    @Test
    void doesNotRepublishWhenSameNotificationAlreadyExistsToday() {
        int leadMonths = 2;
        when(systemSettingsService.getMailLeadMonths()).thenReturn(leadMonths);
        LocalDate feeDueDate = LocalDate.now(KST).plusMonths(leadMonths);
        when(patentMetadataRepository.findAll()).thenReturn(List.of(activePatent("PAT-1", feeDueDate)));
        when(reviewHistoryRepository.findAll()).thenReturn(List.of());
        // 오늘 같은 제목·역할 알림이 이미 존재 → 멱등 발행으로 다시 발행하지 않아야 한다.
        NotificationEntity existing = new NotificationEntity(
                "NOTIF-existing", "연차료 검토 시작 알림", "m", "ADMIN", OffsetDateTime.now(KST), "/admin/review-targets");
        when(notificationRepository.findByTargetRoleInOrderByCreatedAtDesc(List.of("ADMIN")))
                .thenReturn(List.of(existing));

        scheduler.run();

        verify(notificationService, never()).addNotification(
                eq("연차료 검토 시작 알림"), anyString(), eq("ADMIN"), anyString());
    }

    @Test
    void republishesWhenPriorNotificationIsFromAnotherDay() {
        int leadMonths = 2;
        when(systemSettingsService.getMailLeadMonths()).thenReturn(leadMonths);
        LocalDate feeDueDate = LocalDate.now(KST).plusMonths(leadMonths);
        when(patentMetadataRepository.findAll()).thenReturn(List.of(activePatent("PAT-1", feeDueDate)));
        when(reviewHistoryRepository.findAll()).thenReturn(List.of());
        // 같은 제목이지만 어제 발행분 → 멱등 차단 대상이 아니므로 오늘 다시 발행해야 한다.
        NotificationEntity yesterday = new NotificationEntity(
                "NOTIF-yesterday", "연차료 검토 시작 알림", "m", "ADMIN",
                OffsetDateTime.now(KST).minusDays(1), "/admin/review-targets");
        when(notificationRepository.findByTargetRoleInOrderByCreatedAtDesc(List.of("ADMIN")))
                .thenReturn(List.of(yesterday));

        scheduler.run();

        verify(notificationService, times(1)).addNotification(
                eq("연차료 검토 시작 알림"), anyString(), eq("ADMIN"), anyString());
    }

    private static PatentMetadataEntity activePatent(String patentId, LocalDate feeDueDate) {
        return new PatentMetadataEntity(
                patentId,
                "MGMT-" + patentId,
                null,
                "테스트 특허",
                "AI",
                "데이터분석",
                null,
                "KR",
                null,
                null,
                PatentLifecycleStatus.ACTIVE,
                LocalDate.now(KST).minusYears(5),
                LocalDate.now(KST).minusYears(4),
                "APP-1",
                "REG-1",
                LocalDate.now(KST).plusYears(15),
                feeDueDate);
    }
}
