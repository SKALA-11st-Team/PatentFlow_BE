package com.syuuk.patentflow.patent.repository;

import com.syuuk.patentflow.patent.domain.PatentReviewHistoryEntity;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PatentReviewHistoryRepository extends JpaRepository<PatentReviewHistoryEntity, String>, JpaSpecificationExecutor<PatentReviewHistoryEntity> {

    List<PatentReviewHistoryEntity> findByPatentIdOrderByCreatedAtDesc(String patentId);

    Optional<PatentReviewHistoryEntity> findByPatentIdAndQuarterKey(String patentId, String quarterKey);

    // 특정 분기에서 지정된 상태이면서 아직 지연 처리 안된 이력 조회 — is_delayed 플래그 세팅에 사용
    List<PatentReviewHistoryEntity> findByQuarterKeyAndReviewWorkflowStatusInAndDelayedFalse(
            String quarterKey, Collection<ReviewWorkflowStatus> statuses);

    // 특정 분기에서 지정된 상태를 가진 이력 조회 (지연 여부 무관)
    List<PatentReviewHistoryEntity> findByQuarterKeyAndReviewWorkflowStatusIn(
            String quarterKey, Collection<ReviewWorkflowStatus> statuses);

    // 지연된 이력 조회
    List<PatentReviewHistoryEntity> findByDelayedTrue();

    // 특정 분기의 모든 이력 조회
    List<PatentReviewHistoryEntity> findByQuarterKey(String quarterKey);
}
