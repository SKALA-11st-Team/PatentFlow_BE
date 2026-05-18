package com.syuuk.patentflow.patent.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.patent.dto.LegalDashboardSummaryResponse;
import com.syuuk.patentflow.patent.dto.PatentListItemResponse;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.service.PatentFixtureService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/legal")
public class LegalController {

    private final PatentFixtureService patentFixtureService;

    public LegalController(PatentFixtureService patentFixtureService) {
        this.patentFixtureService = patentFixtureService;
    }

    /**
     * @relatedFR LEGAL-01
     * @relatedUI UI-LEGAL-01
     * @description 특허관리자 대시보드용 집계 통계를 반환한다.
     */
    @GetMapping("/dashboard/summary")
    public ApiResponse<LegalDashboardSummaryResponse> getDashboardSummary() {
        List<PatentListItemResponse> all = patentFixtureService.getAllPatents();
        int total = all.size();
        int pendingReview = countByStatus(all, ReviewWorkflowStatus.MAIL_READY);
        int waitingBusiness = countByStatus(all, ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE);
        int businessReceived = countByStatus(all, ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED);
        int pendingLegal = countByStatus(all, ReviewWorkflowStatus.LEGAL_ACTION_RECORDED);
        return ApiResponse.ok(new LegalDashboardSummaryResponse(
                total, pendingReview, waitingBusiness, businessReceived, pendingLegal));
    }

    private int countByStatus(List<PatentListItemResponse> patents, ReviewWorkflowStatus status) {
        return (int) patents.stream().filter(p -> p.reviewWorkflowStatus() == status).count();
    }
}
