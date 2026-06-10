package com.syuuk.patentflow.patent.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.patent.dto.AreaDistributionResponse;
import com.syuuk.patentflow.patent.dto.LegalDashboardSummaryResponse;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.service.DashboardSummaryService;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * @relatedFR LEGAL-01, LEGAL-02
     * @relatedUI UI-LEGAL-01
     * @description DASH-F3: 검토 대상 특허의 사업/기술/제품 영역별 분포를 서버에서 집계해 반환한다.
     *     review-targets 와 동일한 필터(quarter/country/date/status)를 받아 분포 카드와 목록을 정합시킨다.
     */
    @GetMapping("/dashboard/area-distribution")
    public ApiResponse<AreaDistributionResponse> getAreaDistribution(
            @RequestParam(required = false) String quarter,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) ReviewWorkflowStatus reviewWorkflowStatus) {
        return ApiResponse.ok(dashboardSummaryService.getAreaDistribution(
                quarter, country, dateFrom, dateTo, reviewWorkflowStatus));
    }
}
