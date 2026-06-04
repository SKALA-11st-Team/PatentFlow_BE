package com.syuuk.patentflow.patent.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.common.response.PageResponse;
import com.syuuk.patentflow.patent.dto.AssignDepartmentRequest;
import com.syuuk.patentflow.patent.dto.PatentBibliographicInfoResponse;
import com.syuuk.patentflow.patent.dto.PatentContextSuggestionRequest;
import com.syuuk.patentflow.patent.dto.PatentContextSuggestionResponse;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import com.syuuk.patentflow.patent.dto.PatentHistoryResponse;
import com.syuuk.patentflow.patent.dto.PatentReviewHistoryItemResponse;
import com.syuuk.patentflow.patent.dto.PatentListItemResponse;
import com.syuuk.patentflow.patent.dto.PatentUpsertRequest;
import com.syuuk.patentflow.patent.dto.PatentUpsertResponse;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.service.PatentReviewService;
import jakarta.validation.Valid;
import java.time.LocalDate;
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

    private final PatentReviewService patentReviewService;

    public PatentController(PatentReviewService patentReviewService) {
        this.patentReviewService = patentReviewService;
    }

    /**
     * @relatedFR FR-LEGAL-01, FR-LEGAL-02
     * @relatedUI UI-COM-02, UI-LEGAL-02
     * @description 특허 목록 검색/필터링/정렬/페이징 API.
     */
    @GetMapping
    public PageResponse<PatentListItemResponse> getPatents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) ReviewWorkflowStatus reviewWorkflowStatus,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String quarterKey,
            @RequestParam(required = false) Boolean isDelayed) {
        return patentReviewService.getPatents(page, size, keyword, departmentId, reviewWorkflowStatus, sort, quarterKey, isDelayed);
    }

    /**
     * @relatedFR FR-LEGAL-22, FR-LEGAL-24
     * @relatedUI UI-LEGAL-01, UI-COM-02, UI-BUS-01
     * @description 분기/날짜 범위/국가 기준 검토 대상 특허를 조회한다.
     */
    @GetMapping("/review-targets")
    public ApiResponse<List<PatentListItemResponse>> getReviewTargets(
            @RequestParam(required = false) String quarter,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) ReviewWorkflowStatus reviewWorkflowStatus) {
        return ApiResponse.ok(patentReviewService.getReviewTargets(quarter, country, dateFrom, dateTo, reviewWorkflowStatus));
    }

    /**
     * @relatedFR FR-LEGAL-03, FR-LEGAL-04
     * @relatedUI UI-LEGAL-02, UI-LEGAL-03
     * @description 특허 기본 정보와 회사 컨텍스트 등록 API.
     */
    @PostMapping
    public ApiResponse<PatentUpsertResponse> createPatent(@Valid @RequestBody PatentUpsertRequest request) {
        return ApiResponse.ok(patentReviewService.createPatent(request));
    }

    /**
     * @relatedFR FR-LEGAL-03
     * @relatedUI UI-LEGAL-02, UI-LEGAL-03
     * @description 관리번호/출원번호/등록번호 기반 특허 외부 검색 API.
     */
    @GetMapping("/external-lookup")
    public ApiResponse<PatentBibliographicInfoResponse> lookupBibliographicInfo(
            @RequestParam(required = false) String managementNumber,
            @RequestParam(required = false) String registrationNumber,
            @RequestParam(required = false) String sourcePriority
    ) {
        return ApiResponse.ok(patentReviewService.lookupBibliographicInfo(managementNumber, registrationNumber, sourcePriority));
    }

    /**
     * @relatedFR FR-LEGAL-03, FR-LEGAL-04
     * @relatedUI UI-LEGAL-02, UI-LEGAL-03
     * @description 특허명/관련제품 기반 회사 컨텍스트 추천 API.
     */
    @PostMapping("/context-suggestions")
    public ApiResponse<PatentContextSuggestionResponse> suggestContext(
            @RequestBody PatentContextSuggestionRequest request
    ) {
        return ApiResponse.ok(patentReviewService.suggestContext(request));
    }

    /**
     * @relatedFR FR-LEGAL-05, FR-LEGAL-06, FR-LEGAL-07, FR-LEGAL-08, FR-LEGAL-09, FR-LEGAL-10
     * @relatedUI UI-LEGAL-04
     * @description 특허 상세, AI 평가 레포트, 최종 판단 분리 조회 API.
     */
    @GetMapping("/{patentId}")
    public ApiResponse<PatentDetailResponse> getPatentDetail(@PathVariable String patentId) {
        return ApiResponse.ok(patentReviewService.getPatentDetail(patentId));
    }

    /**
     * @relatedFR FR-LEGAL-03, FR-LEGAL-04
     * @relatedUI UI-LEGAL-02, UI-LEGAL-03
     * @description 특허 기본 정보와 회사 컨텍스트 수정 API.
     */
    @PutMapping("/{patentId}")
    public ApiResponse<PatentUpsertResponse> updatePatent(
            @PathVariable String patentId,
            @Valid @RequestBody PatentUpsertRequest request
    ) {
        return ApiResponse.ok(patentReviewService.updatePatent(patentId, request));
    }

    /**
     * @relatedFR FR-LEGAL-11
     * @relatedUI UI-LEGAL-04, UI-BUS-05
     * @description 평가/판단 이력 조회 API.
     */
    @GetMapping("/{patentId}/history")
    public ApiResponse<List<PatentHistoryResponse>> getPatentHistory(@PathVariable String patentId) {
        return ApiResponse.ok(patentReviewService.getPatentHistory(patentId));
    }

    // 특허의 분기별 검토 이력(patent_review_history) 조회 — 과거 분기 이력 페이지에 사용
    @GetMapping("/{patentId}/review-history")
    public ApiResponse<List<PatentReviewHistoryItemResponse>> getReviewHistory(@PathVariable String patentId) {
        return ApiResponse.ok(patentReviewService.getReviewHistory(patentId));
    }

    /**
     * @description 이번 분기 납부 대상 특허에 담당 사업부를 배정한다.
     */
    @PatchMapping("/{patentId}/department")
    public ApiResponse<PatentDetailResponse> assignDepartment(
            @PathVariable String patentId,
            @Valid @RequestBody AssignDepartmentRequest request
    ) {
        return ApiResponse.ok(patentReviewService.assignDepartment(patentId, request.departmentId()));
    }

}
