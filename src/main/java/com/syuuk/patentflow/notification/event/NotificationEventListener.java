package com.syuuk.patentflow.notification.event;

import com.syuuk.patentflow.notification.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * NOTI-04: 워크플로우 이벤트를 알림으로 기록한다. AFTER_COMMIT으로 받아 발행 트랜잭션이 커밋된 뒤에만
 * 알림을 남긴다(롤백 시 미발행). fallbackExecution=true로 트랜잭션 밖(예: 비동기 AI 잡)에서 발행돼도
 * 즉시 처리한다. addNotification은 @Transactional이라 커밋 이후 별도 트랜잭션으로 기록된다.
 */
@Component
public class NotificationEventListener {

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkflowNotification(WorkflowNotificationEvent event) {
        notificationService.addNotification(event.title(), event.message(), event.targetRole(), event.link());
    }
}
