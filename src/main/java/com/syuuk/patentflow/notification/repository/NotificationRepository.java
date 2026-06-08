package com.syuuk.patentflow.notification.repository;

import com.syuuk.patentflow.notification.domain.NotificationEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {

    List<NotificationEntity> findByTargetRoleInOrderByCreatedAtDesc(Collection<String> targetRoles);
}
