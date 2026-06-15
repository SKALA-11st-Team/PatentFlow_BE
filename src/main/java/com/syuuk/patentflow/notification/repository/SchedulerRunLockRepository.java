package com.syuuk.patentflow.notification.repository;

import com.syuuk.patentflow.notification.domain.SchedulerRunLockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulerRunLockRepository extends JpaRepository<SchedulerRunLockEntity, String> {
}
