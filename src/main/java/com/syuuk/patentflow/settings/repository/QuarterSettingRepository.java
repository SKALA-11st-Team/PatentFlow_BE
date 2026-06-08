package com.syuuk.patentflow.settings.repository;

import com.syuuk.patentflow.settings.domain.QuarterSettingEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface QuarterSettingRepository extends JpaRepository<QuarterSettingEntity, String> {

    List<QuarterSettingEntity> findByYearOrderByQuarterNumber(int year);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QuarterSettingEntity q where q.quarterKey = :quarterKey")
    java.util.Optional<QuarterSettingEntity> findByIdForUpdate(String quarterKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QuarterSettingEntity q where q.activated = true and q.ended = false order by q.activatedAt desc")
    List<QuarterSettingEntity> findActiveForUpdate();

    List<QuarterSettingEntity> findByActivatedTrueAndEndedFalseOrderByActivatedAtDesc();
}
