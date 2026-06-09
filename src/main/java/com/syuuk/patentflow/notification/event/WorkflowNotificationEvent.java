package com.syuuk.patentflow.notification.event;

/**
 * NOTI-04: 워크플로우 진행 이벤트를 알림으로 발행하기 위한 도메인 이벤트.
 * 각 서비스는 비즈니스 트랜잭션 안에서 이 이벤트를 publish하고, NotificationEventListener가
 * 트랜잭션 커밋 후(AFTER_COMMIT) 알림을 기록한다 — 롤백된 작업은 알림이 남지 않는다.
 *
 * @param title      알림 제목
 * @param message    알림 본문
 * @param targetRole 대상 역할(ADMIN/BUSINESS/COMMON)
 * @param link       클릭 시 이동 경로(없으면 null)
 */
public record WorkflowNotificationEvent(String title, String message, String targetRole, String link) {
}
