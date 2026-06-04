package com.syuuk.patentflow.settings.repository;

import com.syuuk.patentflow.settings.domain.QuarterSettingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuarterSettingRepository extends JpaRepository<QuarterSettingEntity, String> {

    List<QuarterSettingEntity> findByYearOrderByQuarterNumber(int year);
    
    // [최적화] 전체 분기 설정을 메모리로 가져와서 필터링하는 대신, 활성화된 단건 분기를 DB 단에서 즉시 조회
    QuarterSettingEntity findFirstByActivatedTrueAndEndedFalseOrderByQuarterKeyDesc();
}
