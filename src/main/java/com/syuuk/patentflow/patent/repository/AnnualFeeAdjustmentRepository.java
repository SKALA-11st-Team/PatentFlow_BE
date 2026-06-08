package com.syuuk.patentflow.patent.repository;

import com.syuuk.patentflow.patent.domain.AnnualFeeAdjustmentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnualFeeAdjustmentRepository extends JpaRepository<AnnualFeeAdjustmentEntity, String> {

    List<AnnualFeeAdjustmentEntity> findByPatentIdOrderByAdjustedAtDesc(String patentId);

    List<AnnualFeeAdjustmentEntity> findByPatentIdInOrderByPatentIdAscAdjustedAtDesc(List<String> patentIds);
}
