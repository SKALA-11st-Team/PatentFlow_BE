package com.syuuk.patentflow.notification.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.syuuk.patentflow.notification.service.NotificationService;
import org.junit.jupiter.api.Test;

/**
 * be-notification-3: 알림 발행은 best-effort — addNotification 실패가 발행 트리거(워크플로우 커밋·
 * 비동기 AI 잡 상태)로 전파되지 않도록 리스너가 예외를 삼키는지 가드한다.
 */
class NotificationEventListenerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final NotificationEventListener listener = new NotificationEventListener(notificationService);

    @Test
    void delegatesEventToNotificationService() {
        WorkflowNotificationEvent event =
                new WorkflowNotificationEvent("제목", "본문", "ADMIN", "/admin/patents/PAT-1");

        listener.onWorkflowNotification(event);

        verify(notificationService).addNotification("제목", "본문", "ADMIN", "/admin/patents/PAT-1");
    }

    @Test
    void swallowsNotificationFailureSoItDoesNotPropagateToPublisher() {
        doThrow(new RuntimeException("일시적 DB 오류"))
                .when(notificationService)
                .addNotification(any(), any(), any(), any());
        WorkflowNotificationEvent event =
                new WorkflowNotificationEvent("AI 평가 레포트 생성 완료", "본문", "ADMIN", "/admin/patents/PAT-1");

        // fallbackExecution 경로(비동기 AI 잡)에서 인라인 동기 실행되더라도 예외가 발행 측으로 전파되면
        // 안 된다 — 전파 시 이미 확정된 잡 상태가 FAILED로 오염될 수 있다.
        assertThatCode(() -> listener.onWorkflowNotification(event)).doesNotThrowAnyException();

        verify(notificationService).addNotification(eq("AI 평가 레포트 생성 완료"), any(), eq("ADMIN"), any());
    }
}
