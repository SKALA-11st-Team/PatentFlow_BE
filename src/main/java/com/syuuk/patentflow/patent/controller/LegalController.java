package com.syuuk.patentflow.patent.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.patent.dto.LegalDashboardSummaryResponse;
import com.syuuk.patentflow.patent.dto.PatentListItemResponse;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.service.PatentReviewService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/legal")
public class LegalController {

    private final PatentReviewService patentReviewService;

    public LegalController(PatentReviewService patentReviewService) {
        this.patentReviewService = patentReviewService;
    }

    /**
     * @relatedFR LEGAL-01
     * @relatedUI UI-LEGAL-01
     * @description 특허관리자 대시보드용 집계 통계를 반환한다.
     */
    @GetMapping("/dashboard/summary")
    public ApiResponse<LegalDashboardSummaryResponse> getDashboardSummary() {
        List<PatentListItemResponse> all = patentReviewService.getAllPatents();
        int total = all.size();
        int pendingReview = countByStatus(all, ReviewWorkflowStatus.MAIL_READY);
        int waitingBusiness = countByStatus(all, ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE);
        int businessReceived = countByStatus(all, ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED);
        // LEGAL_ACTION_RECORDED 상태 제거로 legalActionResult 유무로 최종 처리 완료 건수를 집계
        int pendingLegal = (int) all.stream().filter(p -> p.legalActionResult() != null).count();
        return ApiResponse.ok(new LegalDashboardSummaryResponse(
                total, pendingReview, waitingBusiness, businessReceived, pendingLegal));
    }

    private int countByStatus(List<PatentListItemResponse> patents, ReviewWorkflowStatus status) {
        return (int) patents.stream().filter(p -> p.reviewWorkflowStatus() == status).count();
    }
}
