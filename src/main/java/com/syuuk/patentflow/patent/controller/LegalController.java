package com.syuuk.patentflow.patent.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.patent.dto.LegalDashboardSummaryResponse;
import com.syuuk.patentflow.patent.service.DashboardSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/legal")
public class LegalController {

    private final DashboardSummaryService dashboardSummaryService;

    public LegalController(DashboardSummaryService dashboardSummaryService) {
        this.dashboardSummaryService = dashboardSummaryService;
    }

    /**
     * @relatedFR LEGAL-01
     * @relatedUI UI-LEGAL-01
     * @description 특허관리자 대시보드용 집계 통계를 반환한다.
     */
    @GetMapping("/dashboard/summary")
    public ApiResponse<LegalDashboardSummaryResponse> getDashboardSummary() {
        return ApiResponse.ok(dashboardSummaryService.getLegalSummary());
    }
}
