package com.syuuk.patentflow.notification.repository;

import com.syuuk.patentflow.notification.domain.NotificationReadStateEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationReadStateRepository extends JpaRepository<NotificationReadStateEntity, String> {

    List<NotificationReadStateEntity> findByUserIdAndNotificationIdIn(String userId, Collection<String> notificationIds);
}
