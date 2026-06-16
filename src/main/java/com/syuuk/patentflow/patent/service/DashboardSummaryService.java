package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.business.dto.BusinessDashboardSummaryResponse;
import com.syuuk.patentflow.patent.dto.AreaDistributionResponse;
import com.syuuk.patentflow.patent.dto.AreaGroupResponse;
import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import com.syuuk.patentflow.patent.dto.LegalDashboardSummaryResponse;
import com.syuuk.patentflow.patent.dto.PatentListItemResponse;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardSummaryService {

    private final PatentMetadataRepository patentMetadataRepository;
    private final PatentReviewHistoryRepository reviewHistoryRepository;
    private final PatentReviewService patentReviewService;

    public DashboardSummaryService(
            PatentMetadataRepository patentMetadataRepository,
            PatentReviewHistoryRepository reviewHistoryRepository,
            PatentReviewService patentReviewService) {
        this.patentMetadataRepository = patentMetadataRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.patentReviewService = patentReviewService;
    }

    @Transactional(readOnly = true)
    public LegalDashboardSummaryResponse getLegalSummary() {
        int total = Math.toIntExact(patentMetadataRepository.count());
        // DASH-01: 이번 분기 검토 대상 수(NOT_IN_REVIEW 제외 최신 상태) — KPI 분모 단일 출처.
        int quarterlyTargetCount = Math.toIntExact(
                reviewHistoryRepository.countLatestByReviewWorkflowStatusNot(ReviewWorkflowStatus.NOT_IN_REVIEW));
        int mailReadySuccessCount = Math.toIntExact(
                reviewHistoryRepository.countLatestMailReadyWithSuccessfulAiReport(ReviewWorkflowStatus.MAIL_READY));
        int aiReportFailedCount = Math.toIntExact(reviewHistoryRepository.countLatestFailedAiReports());
        // 메일 발송 대기 = MAIL_READY 상태이고 산출물(reportId)이 있는 것(degraded 포함).
        // 클릭 시 이동하는 메일 발송 대기 목록과 같은 기준이라 카운트=목록이 정합한다.
        int pendingReview = Math.toIntExact(
                reviewHistoryRepository.countLatestMailReadyWithReport(ReviewWorkflowStatus.MAIL_READY));
        int waitingBusiness = countLatest(ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE);
        int businessReceived = countLatest(ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED);
        int pendingFinalDecision = Math.toIntExact(reviewHistoryRepository.countLatestPendingLegalAction(
                ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED));
        int legalActionCompleted = Math.toIntExact(reviewHistoryRepository.countLatestByLegalActionResultIsNotNull());
        return new LegalDashboardSummaryResponse(
                total,
                quarterlyTargetCount,
                pendingReview,
                mailReadySuccessCount,
                aiReportFailedCount,
                waitingBusiness,
                businessReceived,
                pendingFinalDecision,
                legalActionCompleted);
    }

    @Transactional(readOnly = true)
    public BusinessDashboardSummaryResponse getBusinessSummary(String departmentId) {
        int total = Math.toIntExact(reviewHistoryRepository.countLatestByDepartmentId(departmentId));
        int pendingReview = Math.toIntExact(reviewHistoryRepository.countLatestByDepartmentIdAndReviewWorkflowStatus(
                departmentId,
                ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE));
        int reviewed = Math.toIntExact(reviewHistoryRepository.countLatestReviewedByDepartmentId(
                departmentId,
                List.of(ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED)));
        int maintained = Math.toIntExact(reviewHistoryRepository.countLatestByDepartmentIdAndBusinessOpinionDecision(
                departmentId,
                BusinessOpinionDecision.MAINTAIN));
        int abandoned = Math.toIntExact(reviewHistoryRepository.countLatestByDepartmentIdAndBusinessOpinionDecision(
                departmentId,
                BusinessOpinionDecision.ABANDON));
        return new BusinessDashboardSummaryResponse(total, pendingReview, reviewed, maintained, abandoned);
    }

    /**
     * DASH-F3: 검토 대상(동일 필터)을 영역별로 서버 집계한다. review-targets 와 같은 조회 경로를 재사용해
     * 분포 카드와 목록 테이블이 항상 같은 모집단을 보게 한다. 색상·비율·정렬 등 표시 로직은 FE가 담당한다.
     */
    @Transactional(readOnly = true)
    public AreaDistributionResponse getAreaDistribution(
            String quarter,
            String country,
            LocalDate dateFrom,
            LocalDate dateTo,
            ReviewWorkflowStatus reviewWorkflowStatus) {
        List<PatentListItemResponse> targets = patentReviewService.getReviewTargets(
                quarter, country, dateFrom, dateTo, reviewWorkflowStatus);
        return new AreaDistributionResponse(
                targets.size(),
                aggregate(targets, PatentListItemResponse::businessArea, PatentListItemResponse::departmentName),
                aggregate(targets, PatentListItemResponse::technologyArea, PatentListItemResponse::businessArea),
                aggregate(targets, PatentListItemResponse::productName, PatentListItemResponse::technologyArea),
                // DASH-F4: 출원 국가별 분포 — TW·UAE 등 KIPRIS 미지원 국가 특허를 국가 기준으로 바로 조회한다.
                aggregate(targets, PatentListItemResponse::country, PatentListItemResponse::businessArea));
    }

    /** 1차값으로 묶어 건수와 (중복 제거된) 보조 라벨을 집계한다. 정렬은 FE에 맡기고 삽입 순서를 보존한다. */
    private List<AreaGroupResponse> aggregate(
            List<PatentListItemResponse> targets,
            Function<PatentListItemResponse, String> primary,
            Function<PatentListItemResponse, String> secondary) {
        Map<String, AreaGroupAccumulator> groups = new LinkedHashMap<>();
        for (PatentListItemResponse target : targets) {
            AreaGroupAccumulator accumulator =
                    groups.computeIfAbsent(displayValue(primary.apply(target)), key -> new AreaGroupAccumulator());
            accumulator.count++;
            accumulator.relatedLabels.add(displayValue(secondary.apply(target)));
        }
        return groups.entrySet().stream()
                .map(entry -> new AreaGroupResponse(
                        entry.getKey(),
                        entry.getValue().count,
                        List.copyOf(entry.getValue().relatedLabels)))
                .toList();
    }

    /** FE BusinessAreaReviewCards.getDisplayValue 와 동일 규칙: 공백·"N/A"는 "미분류" 버킷으로 정규화. */
    private static String displayValue(String raw) {
        String normalized = raw == null ? "" : raw.trim();
        return !normalized.isEmpty() && !"N/A".equals(normalized) ? normalized : "미분류";
    }

    private int countLatest(ReviewWorkflowStatus status) {
        return Math.toIntExact(reviewHistoryRepository.countLatestByReviewWorkflowStatus(status));
    }

    private static final class AreaGroupAccumulator {
        private int count;
        private final Set<String> relatedLabels = new LinkedHashSet<>();
    }
}
