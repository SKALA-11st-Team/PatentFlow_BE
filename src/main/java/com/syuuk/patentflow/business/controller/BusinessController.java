package com.syuuk.patentflow.business.controller;

import com.syuuk.patentflow.business.dto.BusinessChecklistItemResponse;
import com.syuuk.patentflow.business.dto.BusinessChecklistSubmissionRequest;
import com.syuuk.patentflow.business.dto.BusinessSubmissionVersionResponse;
import com.syuuk.patentflow.business.service.BusinessFixtureService;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BusinessController {

    private final BusinessFixtureService businessFixtureService;

    public BusinessController(BusinessFixtureService businessFixtureService) {
        this.businessFixtureService = businessFixtureService;
    }

    /**
     * @relatedFR FR-009
     * @relatedUI UI-005, UI-006
     * @description 사업부 의견 작성용 체크리스트 항목 조회 API.
     */
    @GetMapping("/api/v1/business/checklist-items")
    public ApiResponse<List<BusinessChecklistItemResponse>> getChecklistItems() {
        return ApiResponse.ok(businessFixtureService.getChecklistItems());
    }

    /**
     * @relatedFR FR-009, FR-013
     * @relatedUI UI-009
     * @description 특허별 사업부 제출 이력 조회 API.
     */
    @GetMapping("/api/v1/patents/{patentId}/business-submissions")
    public ApiResponse<List<BusinessSubmissionVersionResponse>> getBusinessSubmissions(@PathVariable String patentId) {
        return ApiResponse.ok(businessFixtureService.getSubmissions(patentId));
    }

    /**
     * @relatedFR FR-009
     * @relatedUI UI-005, UI-006
     * @description 사업부 의견/체크리스트 제출 API.
     */
    @PostMapping("/api/v1/patents/{patentId}/business-submissions")
    public ApiResponse<BusinessChecklistSubmissionRequest> submitBusinessChecklist(
            @PathVariable String patentId,
            @Valid @RequestBody BusinessChecklistSubmissionRequest request
    ) {
        if (!patentId.equals(request.patentId())) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST);
        }
        return ApiResponse.ok(businessFixtureService.submit(patentId, request));
    }
}
