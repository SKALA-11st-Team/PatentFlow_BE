package com.syuuk.patentflow.notification.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.notification.domain.NotificationEntity;
import com.syuuk.patentflow.notification.domain.NotificationReadStateEntity;
import com.syuuk.patentflow.notification.dto.NotificationResponse;
import com.syuuk.patentflow.notification.repository.NotificationReadStateRepository;
import com.syuuk.patentflow.notification.repository.NotificationRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String COMMON = "COMMON";

    private final NotificationRepository notificationRepository;
    private final NotificationReadStateRepository readStateRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationReadStateRepository readStateRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.readStateRepository = readStateRepository;
    }

    @Transactional
    public void addNotification(String title, String message, String targetRole, String link) {
        String id = "NOTIF-" + UUID.randomUUID();
        notificationRepository.save(new NotificationEntity(id, title, message, targetRole, OffsetDateTime.now(KST), link));
    }

    @Transactional
    public List<NotificationResponse> getNotifications(String role, String userId) {
        seedDefaultNotificationsIfNeeded();
        List<NotificationEntity> notifications = visibleNotifications(role);
        Map<String, NotificationReadStateEntity> readStates = readStates(userId, notificationIds(notifications));
        return notifications.stream()
                .map(n -> toResponse(n, readStates.containsKey(n.getNotificationId())
                        && readStates.get(n.getNotificationId()).isRead()))
                .toList();
    }

    @Transactional
    public NotificationResponse updateReadState(String notificationId, boolean isRead, String currentRole, String userId) {
        seedDefaultNotificationsIfNeeded();
        NotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.NOTIFICATION_NOT_FOUND));
        assertVisible(notification, currentRole);
        NotificationReadStateEntity readState = readStateRepository.findById(NotificationReadStateEntity.id(notificationId, userId))
                .orElseGet(() -> new NotificationReadStateEntity(notificationId, userId, false, OffsetDateTime.now(KST)));
        readState.update(isRead, OffsetDateTime.now(KST));
        readStateRepository.save(readState);
        return toResponse(notification, readState.isRead());
    }

    @Transactional
    public long unreadCount(String role, String userId) {
        return getNotifications(role, userId).stream()
                .filter(notification -> !notification.isRead())
                .count();
    }

    @Transactional
    public void markAllRead(String role, String userId) {
        seedDefaultNotificationsIfNeeded();
        visibleNotifications(role).forEach(notification -> {
            NotificationReadStateEntity readState = readStateRepository.findById(NotificationReadStateEntity.id(notification.getNotificationId(), userId))
                    .orElseGet(() -> new NotificationReadStateEntity(notification.getNotificationId(), userId, false, OffsetDateTime.now(KST)));
            readState.update(true, OffsetDateTime.now(KST));
            readStateRepository.save(readState);
        });
    }

    private List<NotificationEntity> visibleNotifications(String role) {
        return notificationRepository.findByTargetRoleInOrderByCreatedAtDesc(List.of(COMMON, role));
    }

    private Map<String, NotificationReadStateEntity> readStates(String userId, Collection<String> notificationIds) {
        if (notificationIds.isEmpty()) {
            return Map.of();
        }
        return readStateRepository.findByUserIdAndNotificationIdIn(userId, notificationIds).stream()
                .collect(Collectors.toMap(NotificationReadStateEntity::getNotificationId, Function.identity()));
    }

    private List<String> notificationIds(List<NotificationEntity> notifications) {
        return notifications.stream()
                .map(NotificationEntity::getNotificationId)
                .toList();
    }

    private void assertVisible(NotificationEntity notification, String currentRole) {
        if (!COMMON.equals(notification.getTargetRole()) && !notification.getTargetRole().equals(currentRole)) {
            throw new PatentFlowException(ErrorCode.UNAUTHORIZED);
        }
    }

    private NotificationResponse toResponse(NotificationEntity notification, boolean isRead) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getTargetRole(),
                isRead,
                notification.getCreatedAt(),
                notification.getLink());
    }

    private void seedDefaultNotificationsIfNeeded() {
        if (notificationRepository.count() > 0) {
            return;
        }
        notificationRepository.saveAll(List.of(
                notification("NOTIF-001", "AI 평가 레포트 생성 완료", "PAT-2026-0002 특허의 AI 평가 레포트가 생성되었습니다.", "ADMIN", -1, "/admin/patents/PAT-2026-0002"),
                notification("NOTIF-002", "사업부 메일 발송 완료", "R&D본부 외 3개 사업부에 검토 요청 메일이 발송되었습니다.", "ADMIN", -2, "/admin/mailing"),
                notification("NOTIF-003", "사업부 의견 수신", "R&D본부에서 PAT-2026-0004 특허에 대한 의견을 제출했습니다.", "ADMIN", -3, "/admin/patents/PAT-2026-0004"),
                notification("NOTIF-005", "검토 요청 도착", "연차료 검토 요청이 도착했습니다. R&D본부 배정 특허 3건을 확인해주세요.", "BUSINESS", -1, "/business/review-requests"),
                notification("NOTIF-006", "검토 마감일 임박", "PAT-2026-0004 특허 검토 회신 기한이 7일 후입니다.", "BUSINESS", -2, "/business/patents/PAT-2026-0004"),
                notification("NOTIF-007", "시스템 점검 안내", "2026-05-20 03:00~05:00 시스템 점검이 예정되어 있습니다.", COMMON, -7, null)));
    }

    private static NotificationEntity notification(
            String id, String title, String message, String targetRole, int daysOffset, String link) {
        return new NotificationEntity(id, title, message, targetRole, OffsetDateTime.now(KST).plusDays(daysOffset), link);
    }
}
