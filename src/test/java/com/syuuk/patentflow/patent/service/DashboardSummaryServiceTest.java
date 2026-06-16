package com.syuuk.patentflow.patent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.syuuk.patentflow.business.dto.BusinessDashboardSummaryResponse;
import com.syuuk.patentflow.patent.dto.AiReportReadinessStatus;
import com.syuuk.patentflow.patent.dto.AreaDistributionResponse;
import com.syuuk.patentflow.patent.dto.AreaGroupResponse;
import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import com.syuuk.patentflow.patent.dto.LegalDashboardSummaryResponse;
import com.syuuk.patentflow.patent.dto.PatentListItemResponse;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardSummaryServiceTest {

    @Mock
    private PatentMetadataRepository patentMetadataRepository;

    @Mock
    private PatentReviewHistoryRepository reviewHistoryRepository;

    @Mock
    private PatentReviewService patentReviewService;

    private DashboardSummaryService dashboardSummaryService;

    @BeforeEach
    void setUp() {
        dashboardSummaryService = new DashboardSummaryService(
                patentMetadataRepository, reviewHistoryRepository, patentReviewService);
    }

    @Test
    void getLegalSummaryUsesSingleSourceCountsIncludingQuarterlyTarget() {
        when(patentMetadataRepository.count()).thenReturn(40L);
        when(reviewHistoryRepository.countLatestByReviewWorkflowStatusNot(ReviewWorkflowStatus.NOT_IN_REVIEW))
                .thenReturn(12L);
        when(reviewHistoryRepository.countLatestMailReadyWithSuccessfulAiReport(ReviewWorkflowStatus.MAIL_READY))
                .thenReturn(3L);
        when(reviewHistoryRepository.countLatestFailedAiReports()).thenReturn(2L);
        // 메일 발송 대기(pendingReview)는 MAIL_READY+산출물(degraded 포함) 독립 카운트가 단일 출처다.
        when(reviewHistoryRepository.countLatestMailReadyWithReport(ReviewWorkflowStatus.MAIL_READY))
                .thenReturn(7L);
        when(reviewHistoryRepository.countLatestByReviewWorkflowStatus(ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE))
                .thenReturn(4L);
        when(reviewHistoryRepository.countLatestByReviewWorkflowStatus(ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED))
                .thenReturn(2L);
        when(reviewHistoryRepository.countLatestPendingLegalAction(ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED))
                .thenReturn(2L);
        when(reviewHistoryRepository.countLatestByLegalActionResultIsNotNull()).thenReturn(5L);

        LegalDashboardSummaryResponse summary = dashboardSummaryService.getLegalSummary();

        assertThat(summary.totalPatents()).isEqualTo(40);
        assertThat(summary.quarterlyTargetCount()).isEqualTo(12);
        assertThat(summary.pendingReview()).isEqualTo(7);
        assertThat(summary.mailReadySuccessCount()).isEqualTo(3);
        assertThat(summary.aiReportFailedCount()).isEqualTo(2);
        assertThat(summary.waitingBusinessResponse()).isEqualTo(4);
        assertThat(summary.businessResponseReceived()).isEqualTo(2);
        assertThat(summary.pendingFinalDecision()).isEqualTo(2);
        assertThat(summary.legalActionCompleted()).isEqualTo(5);
        // deprecated pendingLegalAction은 pendingFinalDecision과 동일 값을 유지(하위호환).
        assertThat(summary.pendingLegalAction()).isEqualTo(summary.pendingFinalDecision());
    }

    @Test
    void getBusinessSummaryCountsReviewedWithSingleLatestReviewedQuery() {
        String departmentId = "DEPT-MFG";
        when(reviewHistoryRepository.countLatestByDepartmentId(departmentId)).thenReturn(5L);
        when(reviewHistoryRepository.countLatestByDepartmentIdAndReviewWorkflowStatus(
                departmentId,
                ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE)).thenReturn(2L);
        when(reviewHistoryRepository.countLatestReviewedByDepartmentId(
                departmentId,
                List.of(ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED))).thenReturn(2L);
        when(reviewHistoryRepository.countLatestByDepartmentIdAndBusinessOpinionDecision(
                departmentId,
                BusinessOpinionDecision.MAINTAIN)).thenReturn(1L);
        when(reviewHistoryRepository.countLatestByDepartmentIdAndBusinessOpinionDecision(
                departmentId,
                BusinessOpinionDecision.ABANDON)).thenReturn(1L);

        BusinessDashboardSummaryResponse summary = dashboardSummaryService.getBusinessSummary(departmentId);

        assertThat(summary.reviewed()).isEqualTo(2);
        verify(reviewHistoryRepository, never())
                .countLatestByDepartmentIdAndLegalActionResultIsNotNull(departmentId);
    }

    @Test
    void getAreaDistributionGroupsByAreaWithRelatedLabelsAndDisplayNormalization() {
        when(patentReviewService.getReviewTargets(null, null, null, null, null)).thenReturn(List.of(
                listItem("AI", "비전", "스마트카메라", "연구소"),
                listItem("AI", "비전", "스마트카메라", "사업부A"),
                listItem("반도체", "공정", "메모리", "사업부B"),
                // 공백 businessArea·"N/A" productName 은 "미분류" 버킷으로 합쳐져야 한다.
                listItem("  ", "공정", "N/A", "사업부B")));

        AreaDistributionResponse distribution =
                dashboardSummaryService.getAreaDistribution(null, null, null, null, null);

        assertThat(distribution.totalCount()).isEqualTo(4);
        assertThat(distribution.businessArea())
                .extracting(AreaGroupResponse::value, AreaGroupResponse::count)
                .containsExactlyInAnyOrder(tuple("AI", 2), tuple("반도체", 1), tuple("미분류", 1));
        AreaGroupResponse ai = distribution.businessArea().stream()
                .filter(group -> group.value().equals("AI"))
                .findFirst()
                .orElseThrow();
        assertThat(ai.relatedLabels()).containsExactlyInAnyOrder("연구소", "사업부A");
        // 제품 분포: 스마트카메라(2), 메모리(1), 미분류(1, "N/A" 정규화).
        assertThat(distribution.product())
                .extracting(AreaGroupResponse::value, AreaGroupResponse::count)
                .containsExactlyInAnyOrder(tuple("스마트카메라", 2), tuple("메모리", 1), tuple("미분류", 1));
    }

    /** 분포 집계 테스트용 최소 PatentListItemResponse(영역/부서명만 의미 있고 나머지는 기본값). */
    private PatentListItemResponse listItem(
            String businessArea, String technologyArea, String productName, String departmentName) {
        return new PatentListItemResponse(
                "PAT", "MGMT", null, null, "title", null,
                businessArea, technologyArea, productName, null, null,
                null, null, null, "DEPT", departmentName,
                null, null, null, null, null, null, null,
                AiReportReadinessStatus.PENDING, null, null,
                false, null);
    }
}
