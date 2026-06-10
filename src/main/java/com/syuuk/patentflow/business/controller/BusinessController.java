package com.syuuk.patentflow.business.controller;

import com.syuuk.patentflow.auth.dto.UserPrincipalResponse;
import com.syuuk.patentflow.auth.service.AuthService;
import com.syuuk.patentflow.business.dto.BusinessChecklistItemResponse;
import com.syuuk.patentflow.business.dto.BusinessChecklistSubmissionRequest;
import com.syuuk.patentflow.business.dto.BusinessDashboardSummaryResponse;
import com.syuuk.patentflow.business.dto.BusinessSubmissionVersionResponse;
import com.syuuk.patentflow.business.service.BusinessFixtureService;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.common.response.PageResponse;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import com.syuuk.patentflow.patent.dto.PatentListItemResponse;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.service.DashboardSummaryService;
import com.syuuk.patentflow.patent.service.PatentReviewService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BusinessController {

    private final BusinessFixtureService businessFixtureService;
    private final PatentReviewService patentReviewService;
    private final AuthService authService;
    private final DashboardSummaryService dashboardSummaryService;

    public BusinessController(
            BusinessFixtureService businessFixtureService,
            PatentReviewService patentReviewService,
            AuthService authService,
            DashboardSummaryService dashboardSummaryService
    ) {
        this.businessFixtureService = businessFixtureService;
        this.patentReviewService = patentReviewService;
        this.authService = authService;
        this.dashboardSummaryService = dashboardSummaryService;
    }

    /**
     * @relatedFR FR-BUS-01
     * @relatedUI UI-LEGAL-04, UI-BUS-03
     * @description 사업부 의견 작성용 체크리스트 항목 조회 API.
     */
    @GetMapping("/api/v1/business/checklist-items")
    public ApiResponse<List<BusinessChecklistItemResponse>> getChecklistItems() {
        return ApiResponse.ok(businessFixtureService.getChecklistItems());
    }

    /**
     * @relatedFR FR-BUS-01
     * @relatedUI UI-BUS-01
     * @description 사업부 대시보드 집계 통계를 반환한다.
     */
    @GetMapping("/api/v1/business/dashboard/summary")
    public ApiResponse<BusinessDashboardSummaryResponse> getDashboardSummary(Authentication authentication) {
        String departmentId = getDepartmentId(authentication);
        return ApiResponse.ok(dashboardSummaryService.getBusinessSummary(departmentId));
    }

    /**
     * @relatedFR FR-BUS-01
     * @relatedUI UI-BUS-01
     * @description 사업부 검토 요청 목록을 조회한다 (WAITING_BUSINESS_RESPONSE 상태).
     */
    @GetMapping("/api/v1/business/review-requests")
    public PageResponse<PatentListItemResponse> getReviewRequests(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        String departmentId = getDepartmentId(authentication);
        return patentReviewService.getReviewRequests(page, size, departmentId);
    }

    /**
     * @relatedFR FR-LEGAL-01, FR-LEGAL-02
     * @relatedUI UI-COM-02, UI-BUS-01
     * @description 사업부 배정 특허 목록을 조회한다.
     */
    @GetMapping("/api/v1/business/patents")
    public PageResponse<PatentListItemResponse> getBusinessPatents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ReviewWorkflowStatus reviewWorkflowStatus,
            Authentication authentication
    ) {
        String departmentId = getDepartmentId(authentication);
        return patentReviewService.getPatents(page, size, keyword, departmentId, reviewWorkflowStatus, null);
    }

    /**
     * @relatedFR FR-LEGAL-05, FR-BUS-01
     * @relatedUI UI-BUS-02
     * @description 사업부 배정 특허 상세를 조회한다.
     */
    @GetMapping("/api/v1/business/patents/{patentId}")
    public ApiResponse<PatentDetailResponse> getBusinessPatentDetail(
            @PathVariable String patentId,
            Authentication authentication
    ) {
        String departmentId = getDepartmentId(authentication);
        PatentDetailResponse detail = patentReviewService.getPatentDetail(patentId);
        if (!departmentId.equals(detail.departmentId())) {
            throw new PatentFlowException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.ok(detail);
    }

    /**
     * @relatedFR FR-BUS-01, FR-LEGAL-11
     * @relatedUI UI-BUS-04, UI-BUS-05
     * @description 특허별 사업부 제출 이력 조회 API.
     */
    @GetMapping("/api/v1/patents/{patentId}/business-submissions")
    public ApiResponse<List<BusinessSubmissionVersionResponse>> getBusinessSubmissions(
            @PathVariable String patentId,
            Authentication authentication
    ) {
        assertCanAccessPatent(patentId, authentication);
        return ApiResponse.ok(businessFixtureService.getSubmissions(patentId));
    }

    /**
     * @relatedFR FR-BUS-01
     * @relatedUI UI-LEGAL-04, UI-BUS-03
     * @description 사업부 의견/체크리스트 제출 API.
     */
    @PostMapping("/api/v1/patents/{patentId}/business-submissions")
    public ApiResponse<BusinessSubmissionVersionResponse> submitBusinessChecklist(
            @PathVariable String patentId,
            @Valid @RequestBody BusinessChecklistSubmissionRequest request,
            Authentication authentication
    ) {
        if (!patentId.equals(request.patentId())) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST);
        }
        assertBusinessDepartmentPatent(patentId, authentication);
        // BIZ-09: 인증 사용자명을 제출자(submittedBy)로 전달해 검증된 작성자를 기록한다.
        return ApiResponse.ok(businessFixtureService.submit(patentId, request, authService.currentUser(authentication).username()));
    }

    private String getDepartmentId(Authentication authentication) {
        if (authentication == null) {
            throw new PatentFlowException(ErrorCode.UNAUTHORIZED);
        }
        UserPrincipalResponse user = authService.currentUser(authentication);
        if (user.departmentId() == null) {
            throw new PatentFlowException(ErrorCode.UNAUTHORIZED);
        }
        return user.departmentId();
    }

    private void assertCanAccessPatent(String patentId, Authentication authentication) {
        if (authentication == null) {
            throw new PatentFlowException(ErrorCode.UNAUTHORIZED);
        }
        UserPrincipalResponse user = authService.currentUser(authentication);
        if ("ADMIN".equals(user.role())) {
            patentReviewService.getPatentDetail(patentId);
            return;
        }
        assertBusinessDepartmentPatent(patentId, authentication);
    }

    private void assertBusinessDepartmentPatent(String patentId, Authentication authentication) {
        if (authentication == null) {
            throw new PatentFlowException(ErrorCode.UNAUTHORIZED);
        }
        String departmentId = getDepartmentId(authentication);
        PatentDetailResponse detail = patentReviewService.getPatentDetail(patentId);
        if (!departmentId.equals(detail.departmentId())) {
            throw new PatentFlowException(ErrorCode.UNAUTHORIZED);
        }
    }

}
