package com.syuuk.patentflow.common.repository;

import com.syuuk.patentflow.common.domain.SystemSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingsRepository extends JpaRepository<SystemSettingsEntity, String> {}
