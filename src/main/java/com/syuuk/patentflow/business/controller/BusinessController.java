/**
 * @author 유건욱
 * @date 2026-05-06
 */
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
import com.syuuk.patentflow.patent.controller.PatentController;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import com.syuuk.patentflow.patent.dto.PatentFeeScheduleResponse;
import com.syuuk.patentflow.patent.dto.PatentListFilter;
import com.syuuk.patentflow.patent.dto.PatentListItemResponse;
import com.syuuk.patentflow.patent.dto.PatentPdfMetaResponse;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.service.AnnualFeeScheduleManagementService;
import com.syuuk.patentflow.patent.service.DashboardSummaryService;
import com.syuuk.patentflow.patent.service.PatentPdfService;
import com.syuuk.patentflow.patent.service.PatentReviewService;
import com.syuuk.patentflow.settings.dto.QuarterSettingResponse;
import com.syuuk.patentflow.settings.service.SettingsService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.ResponseEntity;
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
    private final AnnualFeeScheduleManagementService annualFeeScheduleManagementService;
    private final PatentPdfService patentPdfService;
    private final SettingsService settingsService;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public BusinessController(
            BusinessFixtureService businessFixtureService,
            PatentReviewService patentReviewService,
            AuthService authService,
            DashboardSummaryService dashboardSummaryService,
            AnnualFeeScheduleManagementService annualFeeScheduleManagementService,
            PatentPdfService patentPdfService,
            SettingsService settingsService
    ) {
        this.businessFixtureService = businessFixtureService;
        this.patentReviewService = patentReviewService;
        this.authService = authService;
        this.dashboardSummaryService = dashboardSummaryService;
        this.annualFeeScheduleManagementService = annualFeeScheduleManagementService;
        this.patentPdfService = patentPdfService;
        this.settingsService = settingsService;
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
        return patentReviewService.getPatents(page, size, null, new PatentListFilter(
                keyword, departmentId, reviewWorkflowStatus, null, null, null, null, null, null, null, null));
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
     * @relatedFR FR-LEGAL-24, FR-BUS-01
     * @relatedUI UI-BUS-02
     * @description FEE-06: 사업부 배정 특허의 연차료 일정 조회 — 공유 상세 화면의 일정 카드가 사용한다.
     */
    @GetMapping("/api/v1/business/patents/{patentId}/fee-schedule")
    public ApiResponse<PatentFeeScheduleResponse> getBusinessPatentFeeSchedule(
            @PathVariable String patentId,
            Authentication authentication
    ) {
        assertBusinessDepartmentPatent(patentId, authentication);
        return ApiResponse.ok(annualFeeScheduleManagementService.getPatentFeeSchedule(patentId));
    }

    /**
     * @relatedFR FR-LEGAL-05, FR-BUS-01
     * @description F6: 사업부 배정 특허의 패밀리(같은 관리번호 계열) 조회 — 자기 부서 특허 기준으로만 허용.
     */
    @GetMapping("/api/v1/business/patents/{patentId}/family")
    public ApiResponse<List<PatentListItemResponse>> getBusinessPatentFamily(
            @PathVariable String patentId,
            Authentication authentication
    ) {
        assertBusinessDepartmentPatent(patentId, authentication);
        return ApiResponse.ok(patentReviewService.getPatentFamily(patentId));
    }

    /**
     * @relatedFR FR-LEGAL-13, FR-BUS-01
     * @relatedUI UI-BUS-02
     * @description MAIL-13: 사업부가 배정 특허의 업로드 PDF를 다운로드한다 —
     *     메일의 "시스템에서 다운로드" 안내가 이 경로(특허 상세의 다운로드 버튼)로 이어진다.
     */
    @GetMapping("/api/v1/business/patents/{patentId}/pdf")
    public ResponseEntity<byte[]> downloadBusinessPatentPdf(
            @PathVariable String patentId,
            Authentication authentication
    ) {
        assertBusinessDepartmentPatent(patentId, authentication);
        return PatentController.pdfDownloadResponse(patentPdfService.downloadUploaded(patentId));
    }

    /**
     * @relatedFR FR-LEGAL-13, FR-BUS-01
     * @description MAIL-13: 사업부 화면의 PDF 다운로드 버튼 노출 여부 판단용 첨부 상태 조회.
     */
    @GetMapping("/api/v1/business/patents/{patentId}/pdf/meta")
    public ApiResponse<PatentPdfMetaResponse> getBusinessPatentPdfMeta(
            @PathVariable String patentId,
            Authentication authentication
    ) {
        assertBusinessDepartmentPatent(patentId, authentication);
        return ApiResponse.ok(patentPdfService.meta(patentId));
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
        // FR-LEGAL-12/23: 회신 기한(활성 분기 submissionDeadline) 경과 후에는 의견 제출을 거부한다(접근 윈도우 게이트).
        // 로그인·읽기는 이번 범위에서 막지 않는다(읽기 허용) — 차단은 쓰기 액션인 제출에만 한정한다.
        assertWithinResponseDeadline();
        // BIZ-09: 인증 사용자명을 제출자(submittedBy)로 전달해 검증된 작성자를 기록한다.
        return ApiResponse.ok(businessFixtureService.submit(patentId, request, authService.currentUser(authentication).username()));
    }

    /**
     * 접근 윈도우 게이트: 활성 분기 회신 기한(submissionDeadline)이 경과했으면 제출을 거부한다.
     * 활성 분기가 없거나 기한 미설정이면 게이트를 적용하지 않는다(기존 동작 보존).
     */
    private void assertWithinResponseDeadline() {
        QuarterSettingResponse active = settingsService.getActiveQuarter();
        if (active == null || active.submissionDeadline() == null) {
            return;
        }
        if (LocalDate.now(KST).isAfter(active.submissionDeadline())) {
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS,
                    "회신 기한이 종료되어 의견을 제출할 수 없습니다.");
        }
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
