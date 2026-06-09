package com.syuuk.patentflow.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.notification.dto.NotificationResponse;
import com.syuuk.patentflow.notification.repository.NotificationReadStateRepository;
import com.syuuk.patentflow.notification.repository.NotificationRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * NOTI-09: 알림 도메인 회귀 가드 — 역할 가시성, 사용자별 읽음 격리(NOTI-03), 미존재 404,
 * 타role 접근 거부, 모두읽음/미확인 카운트.
 */
@DataJpaTest
class NotificationServiceTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationReadStateRepository readStateRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, readStateRepository);
    }

    @Test
    void getNotificationsReturnsCommonAndRoleOnly() {
        service.addNotification("관리자용", "m", "ADMIN", null);
        service.addNotification("사업부용", "m", "BUSINESS", null);
        service.addNotification("공통", "m", "COMMON", null);

        List<NotificationResponse> adminView = service.getNotifications("ADMIN", "user-admin");

        assertThat(adminView).extracting(NotificationResponse::title)
                .containsExactlyInAnyOrder("관리자용", "공통");
    }

    @Test
    void updateReadStateIsPerUser() {
        service.addNotification("알림", "m", "ADMIN", null);
        String id = service.getNotifications("ADMIN", "user-1").get(0).notificationId();

        service.updateReadState(id, true, "ADMIN", "user-1");

        assertThat(service.getNotifications("ADMIN", "user-1").get(0).isRead()).isTrue();
        // NOTI-03: 다른 사용자는 읽음 상태를 공유하지 않는다.
        assertThat(service.getNotifications("ADMIN", "user-2").get(0).isRead()).isFalse();
    }

    @Test
    void unknownNotificationThrowsNotFound() {
        assertThatThrownBy(() -> service.updateReadState("NOTIF-unknown", true, "ADMIN", "user-1"))
                .isInstanceOf(PatentFlowException.class)
                .satisfies(e -> assertThat(((PatentFlowException) e).errorCode())
                        .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND));
    }

    @Test
    void crossRoleReadStateIsUnauthorized() {
        service.addNotification("사업부 전용", "m", "BUSINESS", null);
        String id = service.getNotifications("BUSINESS", "biz-1").get(0).notificationId();

        assertThatThrownBy(() -> service.updateReadState(id, true, "ADMIN", "admin-1"))
                .isInstanceOf(PatentFlowException.class)
                .satisfies(e -> assertThat(((PatentFlowException) e).errorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void markAllReadClearsUnreadCount() {
        service.addNotification("a", "m", "ADMIN", null);
        service.addNotification("b", "m", "ADMIN", null);
        assertThat(service.unreadCount("ADMIN", "user-1")).isEqualTo(2);

        service.markAllRead("ADMIN", "user-1");

        assertThat(service.unreadCount("ADMIN", "user-1")).isZero();
    }
}
