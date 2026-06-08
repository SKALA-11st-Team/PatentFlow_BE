package com.syuuk.patentflow.common.repository;

import com.syuuk.patentflow.common.domain.SystemSettingsEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SystemSettingsRepository extends JpaRepository<SystemSettingsEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SystemSettingsEntity s where s.key = :key")
    Optional<SystemSettingsEntity> findByIdForUpdate(String key);
}
