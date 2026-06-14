package com.syuuk.patentflow.notification.event;

import com.syuuk.patentflow.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * NOTI-04: 워크플로우 이벤트를 알림으로 기록한다. AFTER_COMMIT으로 받아 발행 트랜잭션이 커밋된 뒤에만
 * 알림을 남긴다(롤백 시 미발행). fallbackExecution=true로 트랜잭션 밖(예: 비동기 AI 잡)에서 발행돼도
 * 즉시 처리한다. addNotification은 @Transactional이라 커밋 이후 별도 트랜잭션으로 기록된다.
 *
 * be-notification-3: 알림 기록은 항상 best-effort다 — addNotification 실패(일시적 DB 오류 등)를
 * 로깅 후 삼켜 발행 측으로 예외를 전파하지 않는다. AFTER_COMMIT 경로에서는 이미 커밋된 본 트랜잭션
 * 결과에 영향이 없어야 하고, fallbackExecution(비동기 AI 잡 runJob 등) 경로에서는 인라인 동기 실행
 * 이므로 예외가 전파되면 이미 확정된 잡 상태를 FAILED로 오염시킬 수 있어 차단해야 한다.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkflowNotification(WorkflowNotificationEvent event) {
        try {
            notificationService.addNotification(event.title(), event.message(), event.targetRole(), event.link());
        } catch (Exception exception) {
            // 알림 기록 실패는 발행 트리거(워크플로우 커밋·AI 잡 상태)에 영향을 주지 않는다.
            log.warn("[Notification] 알림 발행 실패 — 제목='{}', 역할={}: {}",
                    event.title(), event.targetRole(), exception.getMessage());
        }
    }
}
