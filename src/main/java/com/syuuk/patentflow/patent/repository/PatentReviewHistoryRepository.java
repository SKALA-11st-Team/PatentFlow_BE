package com.syuuk.patentflow.patent.repository;

import com.syuuk.patentflow.patent.domain.PatentReviewHistoryEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PatentReviewHistoryRepository extends JpaRepository<PatentReviewHistoryEntity, String>, JpaSpecificationExecutor<PatentReviewHistoryEntity> {

    List<PatentReviewHistoryEntity> findByPatentIdOrderByCreatedAtDesc(String patentId);

    Optional<PatentReviewHistoryEntity> findByPatentIdAndQuarterKey(String patentId, String quarterKey);
}
