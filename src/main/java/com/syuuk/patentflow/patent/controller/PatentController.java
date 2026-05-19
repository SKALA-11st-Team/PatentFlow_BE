package com.syuuk.patentflow.patent.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.common.response.PageResponse;
import com.syuuk.patentflow.patent.dto.AssignDepartmentRequest;
import com.syuuk.patentflow.patent.dto.BatchPatentIdsRequest;
import com.syuuk.patentflow.patent.dto.ExecutiveApprovalBulkDecisionRequest;
import com.syuuk.patentflow.patent.dto.ExecutiveApprovalBulkDecisionResponse;
import com.syuuk.patentflow.patent.dto.FinalDecisionRequest;
import com.syuuk.patentflow.patent.dto.FinalDecisionResponse;
import com.syuuk.patentflow.patent.dto.PatchFinalDecisionRequest;
import com.syuuk.patentflow.patent.dto.PatentBibliographicInfoResponse;
import com.syuuk.patentflow.patent.dto.PatentContextSuggestionRequest;
import com.syuuk.patentflow.patent.dto.PatentContextSuggestionResponse;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import com.syuuk.patentflow.patent.dto.PatentHistoryResponse;
import com.syuuk.patentflow.patent.dto.PatentListItemResponse;

import com.syuuk.patentflow.patent.dto.PatentUpsertRequest;
import com.syuuk.patentflow.patent.dto.PatentUpsertResponse;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.service.PatentFixtureService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patents")
public class PatentController {

    private final PatentFixtureService patentFixtureService;

    public PatentController(PatentFixtureService patentFixtureService) {
        this.patentFixtureService = patentFixtureService;
    }

    /**
     * @relatedFR FR-001, FR-002
     * @relatedUI UI-002, UI-003
     * @description 특허 목록 검색/필터링/정렬/페이징 API.
     */
    @GetMapping
    public PageResponse<PatentListItemResponse> getPatents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) ReviewWorkflowStatus reviewWorkflowStatus,
            @RequestParam(required = false) String sort) {
        return patentFixtureService.getPatents(page, size, keyword, departmentId, reviewWorkflowStatus, sort);
    }

    /**
     * @relatedFR FR-003, FR-004
     * @relatedUI UI-004
     * @description 특허 기본 정보와 회사 컨텍스트 등록 API.
     */
    @PostMapping
    public ApiResponse<PatentUpsertResponse> createPatent(@Valid @RequestBody PatentUpsertRequest request) {
        return ApiResponse.ok(patentFixtureService.createPatent(request));
    }

    /**
     * @relatedFR FR-003
     * @relatedUI UI-004
     * @description 관리번호/출원번호/등록번호 기반 특허 외부 검색 mock API.
     */
    @GetMapping("/external-lookup")
    public ApiResponse<PatentBibliographicInfoResponse> lookupBibliographicInfo(
            @RequestParam(required = false) String managementNumber,
            @RequestParam(required = false) String registrationNumber,
            @RequestParam(required = false) String sourcePriority
    ) {
        return ApiResponse.ok(patentFixtureService.lookupBibliographicInfo(managementNumber, registrationNumber, sourcePriority));
    }

    /**
     * @relatedFR FR-003, FR-004
     * @relatedUI UI-004
     * @description 특허명/관련제품 기반 회사 컨텍스트 추천 mock API.
     */
    @PostMapping("/context-suggestions")
    public ApiResponse<PatentContextSuggestionResponse> suggestContext(
            @RequestBody PatentContextSuggestionRequest request
    ) {
        return ApiResponse.ok(patentFixtureService.suggestContext(request));
    }

    /**
     * @relatedFR FR-005, FR-006, FR-007, FR-008, FR-011, FR-012
     * @relatedUI UI-005
     * @description 특허 상세, AI 평가 레포트, 최종 판단 분리 조회 API.
     */
    @GetMapping("/{patentId}")
    public ApiResponse<PatentDetailResponse> getPatentDetail(@PathVariable String patentId) {
        return ApiResponse.ok(patentFixtureService.getPatentDetail(patentId));
    }

    /**
     * @relatedFR FR-003, FR-004
     * @relatedUI UI-004
     * @description 특허 기본 정보와 회사 컨텍스트 수정 API.
     */
    @PutMapping("/{patentId}")
    public ApiResponse<PatentUpsertResponse> updatePatent(
            @PathVariable String patentId,
            @Valid @RequestBody PatentUpsertRequest request
    ) {
        return ApiResponse.ok(patentFixtureService.updatePatent(patentId, request));
    }

    /**
     * @relatedFR FR-013
     * @relatedUI UI-005, UI-009
     * @description 평가/판단 이력 조회 API.
     */
    @GetMapping("/{patentId}/history")
    public ApiResponse<List<PatentHistoryResponse>> getPatentHistory(@PathVariable String patentId) {
        return ApiResponse.ok(patentFixtureService.getPatentHistory(patentId));
    }

    /**
     * @relatedFR FR-011, FR-012
     * @relatedUI UI-005
     * @description 특허 최종 판단을 기록하는 API.
     */
    @PostMapping("/{patentId}/final-decision")
    public ApiResponse<FinalDecisionResponse> recordFinalDecision(
            @PathVariable String patentId,
            @Valid @RequestBody FinalDecisionRequest request
    ) {
        return ApiResponse.ok(patentFixtureService.recordFinalDecision(patentId, request));
    }

    /**
     * @relatedFR LEGAL-23
     * @relatedUI UI-005
     * @description 특허 최종 판단을 수정하거나 취소하는 API.
     */
    @PatchMapping("/{patentId}/final-decision")
    public ApiResponse<FinalDecisionResponse> patchFinalDecision(
            @PathVariable String patentId,
            @RequestBody PatchFinalDecisionRequest request
    ) {
        return ApiResponse.ok(patentFixtureService.patchFinalDecision(patentId, request));
    }

    /**
     * @description 이번 분기 납부 대상 특허에 담당 사업부를 배정한다.
     */
    @PatchMapping("/{patentId}/department")
    public ApiResponse<PatentDetailResponse> assignDepartment(
            @PathVariable String patentId,
            @Valid @RequestBody AssignDepartmentRequest request
    ) {
        return ApiResponse.ok(patentFixtureService.assignDepartment(patentId, request.departmentId()));
    }

    /**
     * @description AI 평가 레포트 생성 요청 — FastAPI agent 호출 후 상태를 REPORT_GENERATED로 전환.
     */
    @PostMapping("/{patentId}/request-ai-report")
    public ApiResponse<PatentDetailResponse> requestAiReport(@PathVariable String patentId) {
        return ApiResponse.ok(patentFixtureService.generateAiReport(patentId));
    }

    /**
     * @description 복수 특허를 MAIL_READY 상태로 일괄 전환하는 API.
     */
    @PostMapping("/batch/mark-mail-ready")
    public ApiResponse<List<String>> markMailReady(@Valid @RequestBody BatchPatentIdsRequest request) {
        return ApiResponse.ok(patentFixtureService.markMailReady(request.patentIds()));
    }

    /**
     * @relatedFR FR-011, FR-012
     * @relatedUI UI-005
     * @description 특허 최종 의사결정을 일괄 반영하는 API.
     */
    @PostMapping("/executive-approvals/bulk-decision")
    public ApiResponse<ExecutiveApprovalBulkDecisionResponse> applyExecutiveApproval(
            @Valid @RequestBody ExecutiveApprovalBulkDecisionRequest request
    ) {
        List<String> updatedPatentIds = patentFixtureService.applyExecutiveApproval(request.patentIds(), request.decision());
        return ApiResponse.ok(new ExecutiveApprovalBulkDecisionResponse(
                request.decision(),
                updatedPatentIds.size(),
                updatedPatentIds,
                List.of()));
    }
}
