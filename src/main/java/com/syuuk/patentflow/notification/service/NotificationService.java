package com.syuuk.patentflow.notification.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.notification.dto.NotificationResponse;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final List<NotificationResponse> notifications = new ArrayList<>(List.of(
            notification("NOTIF-001", "AI 평가 레포트 생성 완료", "PAT-2026-0002 특허의 AI 평가 레포트가 생성되었습니다.", "ADMIN", false, -1, "/admin/patents/PAT-2026-0002"),
            notification("NOTIF-002", "사업부 메일 발송 완료", "R&D본부 외 3개 사업부에 검토 요청 메일이 발송되었습니다.", "ADMIN", false, -2, "/admin/mailing"),
            notification("NOTIF-003", "사업부 의견 수신", "R&D본부에서 PAT-2026-0004 특허에 대한 의견을 제출했습니다.", "ADMIN", true, -3, "/admin/patents/PAT-2026-0004"),
            notification("NOTIF-005", "검토 요청 도착", "연차료 검토 요청이 도착했습니다. R&D본부 배정 특허 3건을 확인해주세요.", "BUSINESS", false, -1, "/business/review-requests"),
            notification("NOTIF-006", "검토 마감일 임박", "PAT-2026-0004 특허 검토 마감일이 7일 후입니다.", "BUSINESS", false, -2, "/business/patents/PAT-2026-0004"),
            notification("NOTIF-007", "시스템 점검 안내", "2026-05-20 03:00~05:00 시스템 점검이 예정되어 있습니다.", "COMMON", true, -7, null)));

    public void addNotification(String title, String message, String targetRole, String link) {
        String id = "NOTIF-" + System.currentTimeMillis();
        notifications.add(0, new NotificationResponse(id, title, message, targetRole, false, OffsetDateTime.now(KST), link));
    }

    public List<NotificationResponse> getNotifications(String role) {
        return notifications.stream()
                .filter(n -> "COMMON".equals(n.targetRole()) || n.targetRole().equals(role))
                .toList();
    }

    public NotificationResponse updateReadState(String notificationId, boolean isRead, String currentRole) {
        for (int i = 0; i < notifications.size(); i++) {
            NotificationResponse n = notifications.get(i);
            if (n.notificationId().equals(notificationId)) {
                if (!"COMMON".equals(n.targetRole()) && !n.targetRole().equals(currentRole)) {
                    throw new PatentFlowException(ErrorCode.UNAUTHORIZED);
                }
                NotificationResponse updated = new NotificationResponse(
                        n.notificationId(), n.title(), n.message(), n.targetRole(), isRead, n.createdAt(), n.link());
                notifications.set(i, updated);
                return updated;
            }
        }
        throw new PatentFlowException(ErrorCode.INVALID_REQUEST);
    }

    private static NotificationResponse notification(
            String id, String title, String message, String targetRole, boolean isRead, int daysOffset, String link) {
        OffsetDateTime createdAt = OffsetDateTime.now(KST).plusDays(daysOffset);
        return new NotificationResponse(id, title, message, targetRole, isRead, createdAt, link);
    }
}
