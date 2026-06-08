package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.business.dto.BusinessDashboardSummaryResponse;
import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import com.syuuk.patentflow.patent.dto.LegalDashboardSummaryResponse;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardSummaryService {

    private final PatentMetadataRepository patentMetadataRepository;
    private final PatentReviewHistoryRepository reviewHistoryRepository;

    public DashboardSummaryService(
            PatentMetadataRepository patentMetadataRepository,
            PatentReviewHistoryRepository reviewHistoryRepository) {
        this.patentMetadataRepository = patentMetadataRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
    }

    @Transactional(readOnly = true)
    public LegalDashboardSummaryResponse getLegalSummary() {
        int total = Math.toIntExact(patentMetadataRepository.count());
        int pendingReview = countLatest(ReviewWorkflowStatus.MAIL_READY);
        int waitingBusiness = countLatest(ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE);
        int businessReceived = countLatest(ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED);
        int pendingFinalDecision = Math.toIntExact(reviewHistoryRepository.countLatestPendingLegalAction(
                ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED));
        int legalActionCompleted = Math.toIntExact(reviewHistoryRepository.countLatestByLegalActionResultIsNotNull());
        return new LegalDashboardSummaryResponse(
                total,
                pendingReview,
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

    private int countLatest(ReviewWorkflowStatus status) {
        return Math.toIntExact(reviewHistoryRepository.countLatestByReviewWorkflowStatus(status));
    }
}
