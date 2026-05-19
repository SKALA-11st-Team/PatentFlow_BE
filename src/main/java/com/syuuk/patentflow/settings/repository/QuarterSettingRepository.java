package com.syuuk.patentflow.settings.repository;

import com.syuuk.patentflow.settings.domain.QuarterSettingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuarterSettingRepository extends JpaRepository<QuarterSettingEntity, String> {

    List<QuarterSettingEntity> findByYearOrderByQuarterNumber(int year);
}
