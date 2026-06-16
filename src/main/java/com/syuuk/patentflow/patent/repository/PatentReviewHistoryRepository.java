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

    /** SETTINGS-11: 유지 결정 회차 산정용 — 해당 특허의 누적 최종 결정(MAINTAINED 등) 건수. */
    long countByPatentIdAndLegalActionResult(
            String patentId, com.syuuk.patentflow.patent.dto.LegalActionResult legalActionResult);

    Optional<PatentReviewHistoryEntity> findByPatentIdAndQuarterKey(String patentId, String quarterKey);

    boolean existsByDepartmentId(String departmentId);

    /**
     * 여러 특허의 최신(가장 최근 createdAt) 이력 행을 한 번의 쿼리로 조회한다.
     * 목록/페이징 조회 시 특허마다 이력을 따로 읽던 N+1을 제거하기 위한 배치 조회용.
     */
    @Query("""
            select h
            from PatentReviewHistoryEntity h
            where h.patentId in :patentIds
              and h.createdAt = (
                  select max(latest.createdAt)
                  from PatentReviewHistoryEntity latest
                  where latest.patentId = h.patentId
              )
            """)
    List<PatentReviewHistoryEntity> findLatestByPatentIds(@Param("patentIds") List<String> patentIds);

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
            where h.reviewWorkflowStatus = :status
              and coalesce(h.aiDegraded, false) = false
              and (h.aiFailureReason is null or trim(h.aiFailureReason) = '')
              and h.aiReportId is not null
              and h.createdAt = (
                  select max(latest.createdAt)
                  from PatentReviewHistoryEntity latest
                  where latest.patentId = h.patentId
              )
            """)
    long countLatestMailReadyWithSuccessfulAiReport(@Param("status") ReviewWorkflowStatus status);

    // DASH-01: '메일 발송 대기'(pendingReview) 단일 출처. 메일 발송 가능 = MAIL_READY 상태이고
    // 레포트 산출물(reportId)이 있는 것. degraded(제한 근거)도 산출물이 있으면 발송 대상이므로 포함한다
    // (degraded는 생성 실패가 아니다). 이렇게 해야 클릭 시 이동하는 메일 발송 대기 목록(MAIL_READY)과
    // 카운트가 정합한다.
    @Query("""
            select count(h)
            from PatentReviewHistoryEntity h
            where h.reviewWorkflowStatus = :status
              and h.aiReportId is not null
              and h.createdAt = (
                  select max(latest.createdAt)
                  from PatentReviewHistoryEntity latest
                  where latest.patentId = h.patentId
              )
            """)
    long countLatestMailReadyWithReport(@Param("status") ReviewWorkflowStatus status);

    // DASH-01: 진짜 '생성 실패'(산출물 없음)만 센다. degraded(제한 근거)는 reportId가 있는 정상 산출물이므로
    // 실패로 세지 않는다(aiReportReadinessStatus 정의와 일관). NOT_IN_REVIEW(검토 종료/과거 분기)는 제외한다.
    @Query("""
            select count(h)
            from PatentReviewHistoryEntity h
            where h.aiReportId is null
              and (coalesce(h.aiDegraded, false) = true or (h.aiFailureReason is not null and trim(h.aiFailureReason) <> ''))
              and h.reviewWorkflowStatus <> com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus.NOT_IN_REVIEW
              and h.createdAt = (
                  select max(latest.createdAt)
                  from PatentReviewHistoryEntity latest
                  where latest.patentId = h.patentId
              )
            """)
    long countLatestFailedAiReports();

    // DASH-01: 이번 분기 검토 대상 수 — 최신 상태가 NOT_IN_REVIEW가 아닌 특허(검토 workflow 진행 중) 카운트.
    @Query("""
            select count(h)
            from PatentReviewHistoryEntity h
            where h.reviewWorkflowStatus <> :status
              and h.createdAt = (
                  select max(latest.createdAt)
                  from PatentReviewHistoryEntity latest
                  where latest.patentId = h.patentId
              )
            """)
    long countLatestByReviewWorkflowStatusNot(@Param("status") ReviewWorkflowStatus status);

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
            where h.reviewWorkflowStatus = :status
              and h.legalActionResult is null
              and h.createdAt = (
                  select max(latest.createdAt)
                  from PatentReviewHistoryEntity latest
                  where latest.patentId = h.patentId
              )
            """)
    long countLatestPendingLegalAction(@Param("status") ReviewWorkflowStatus status);

    @Query("""
            select count(h)
            from PatentReviewHistoryEntity h
            where h.departmentId = :departmentId
              and (h.reviewWorkflowStatus in :statuses or h.legalActionResult is not null)
              and h.createdAt = (
                  select max(latest.createdAt)
                  from PatentReviewHistoryEntity latest
                  where latest.patentId = h.patentId
              )
            """)
    long countLatestReviewedByDepartmentId(
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
