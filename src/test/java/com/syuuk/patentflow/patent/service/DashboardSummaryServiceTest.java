package com.syuuk.patentflow.patent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.syuuk.patentflow.business.dto.BusinessDashboardSummaryResponse;
import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import com.syuuk.patentflow.patent.dto.LegalDashboardSummaryResponse;
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

    private DashboardSummaryService dashboardSummaryService;

    @BeforeEach
    void setUp() {
        dashboardSummaryService = new DashboardSummaryService(patentMetadataRepository, reviewHistoryRepository);
    }

    @Test
    void getLegalSummaryUsesSingleSourceCountsIncludingQuarterlyTarget() {
        when(patentMetadataRepository.count()).thenReturn(40L);
        when(reviewHistoryRepository.countLatestByReviewWorkflowStatusNot(ReviewWorkflowStatus.NOT_IN_REVIEW))
                .thenReturn(12L);
        when(reviewHistoryRepository.countLatestByReviewWorkflowStatus(ReviewWorkflowStatus.MAIL_READY)).thenReturn(3L);
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
        assertThat(summary.pendingReview()).isEqualTo(3);
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
}
