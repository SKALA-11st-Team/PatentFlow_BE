package com.syuuk.patentflow.patent.repository;

import com.syuuk.patentflow.patent.domain.PatentReviewHistoryEntity;
import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatentReviewHistoryRepository extends JpaRepository<PatentReviewHistoryEntity, String>, JpaSpecificationExecutor<PatentReviewHistoryEntity> {

    List<PatentReviewHistoryEntity> findByPatentIdOrderByCreatedAtDesc(String patentId);

    Optional<PatentReviewHistoryEntity> findByPatentIdAndQuarterKey(String patentId, String quarterKey);

    @Query("""
            select count(h)
            from PatentReviewHistoryEntity h
            where h.reviewWorkflowStatus = :status
              and h.createdAt = (
                  select max(latest.createdAt)
                  from PatentReviewHistoryEntity latest
                  where latest.patentId = h.patentId
              )
            """)
    long countLatestByReviewWorkflowStatus(@Param("status") ReviewWorkflowStatus status);

    @Query("""
            select count(h)
            from PatentReviewHistoryEntity h
            where h.departmentId = :departmentId
              and h.createdAt = (
                  select max(latest.createdAt)
                  from PatentReviewHistoryEntity latest
                  where latest.patentId = h.patentId
              )
            """)
    long countLatestByDepartmentId(@Param("departmentId") String departmentId);

    @Query("""
            select count(h)
            from PatentReviewHistoryEntity h
            where h.departmentId = :departmentId
              and h.reviewWorkflowStatus = :status
              and h.createdAt = (
                  select max(latest.createdAt)
                  from PatentReviewHistoryEntity latest
                  where latest.patentId = h.patentId
              )
            """)
    long countLatestByDepartmentIdAndReviewWorkflowStatus(
            @Param("departmentId") String departmentId,
            @Param("status") ReviewWorkflowStatus status);

    @Query("""
            select count(h)
            from PatentReviewHistoryEntity h
            where h.departmentId = :departmentId
              and h.reviewWorkflowStatus in :statuses
              and h.createdAt = (
                  select max(latest.createdAt)
                  from PatentReviewHistoryEntity latest
                  where latest.patentId = h.patentId
              )
            """)
    long countLatestByDepartmentIdAndReviewWorkflowStatusIn(
            @Param("departmentId") String departmentId,
            @Param("statuses") List<ReviewWorkflowStatus> statuses);

    @Query("""
            select count(h)
            from PatentReviewHistoryEntity h
            where h.legalActionResult is not null
              and h.createdAt = (
                  select max(latest.createdAt)
                  from PatentReviewHistoryEntity latest
                  where latest.patentId = h.patentId
              )
            """)
    long countLatestByLegalActionResultIsNotNull();

    @Query("""
            select count(h)
            from PatentReviewHistoryEntity h
            where h.departmentId = :departmentId
              and h.legalActionResult is not null
              and h.createdAt = (
                  select max(latest.createdAt)
                  from PatentReviewHistoryEntity latest
                  where latest.patentId = h.patentId
              )
            """)
    long countLatestByDepartmentIdAndLegalActionResultIsNotNull(@Param("departmentId") String departmentId);

    @Query("""
            select count(h)
            from PatentReviewHistoryEntity h
            where h.departmentId = :departmentId
              and h.businessOpinionDecision = :decision
              and h.createdAt = (
                  select max(latest.createdAt)
                  from PatentReviewHistoryEntity latest
                  where latest.patentId = h.patentId
              )
            """)
    long countLatestByDepartmentIdAndBusinessOpinionDecision(
            @Param("departmentId") String departmentId,
            @Param("decision") BusinessOpinionDecision decision);
}
