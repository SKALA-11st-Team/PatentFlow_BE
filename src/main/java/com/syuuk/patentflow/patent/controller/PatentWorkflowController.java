package com.syuuk.patentflow.patent.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.patent.dto.BatchAiReportResult;
import com.syuuk.patentflow.patent.dto.BatchPatentIdsRequest;
import com.syuuk.patentflow.patent.dto.FinalDecisionRequest;
import com.syuuk.patentflow.patent.dto.FinalDecisionResponse;
import com.syuuk.patentflow.patent.dto.PatchFinalDecisionRequest;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import com.syuuk.patentflow.patent.dto.ResponseDeadlineExtensionRequest;
import com.syuuk.patentflow.patent.service.PatentWorkflowService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patents")
public class PatentWorkflowController {

    private final PatentWorkflowService patentWorkflowService;

    public PatentWorkflowController(PatentWorkflowService patentWorkflowService) {
        this.patentWorkflowService = patentWorkflowService;
    }

    /**
     * @relatedFR FR-LEGAL-09, FR-LEGAL-10
     * @relatedUI UI-LEGAL-04
     * @description 특허 최종 판단을 기록하는 API.
     */
    @PostMapping("/{patentId}/final-decision")
    public ApiResponse<FinalDecisionResponse> recordFinalDecision(
            @PathVariable String patentId,
            @Valid @RequestBody FinalDecisionRequest request
    ) {
        return ApiResponse.ok(patentWorkflowService.recordFinalDecision(patentId, request));
    }

    /**
     * @description 지연 관리 화면에서 이전 분기 미완료 이력을 최종 처리한다.
     */
    @PostMapping("/{patentId}/delayed-reviews/{quarterKey}/final-decision")
    public ApiResponse<FinalDecisionResponse> recordDelayedFinalDecision(
            @PathVariable String patentId,
            @PathVariable String quarterKey,
            @Valid @RequestBody FinalDecisionRequest request
    ) {
        return ApiResponse.ok(patentWorkflowService.recordDelayedFinalDecision(patentId, quarterKey, request));
    }

    /**
     * @relatedFR FR-LEGAL-20
     * @relatedUI UI-LEGAL-04
     * @description 특허 최종 판단을 수정하거나 취소하는 API.
     */
    @PatchMapping("/{patentId}/final-decision")
    public ApiResponse<FinalDecisionResponse> patchFinalDecision(
            @PathVariable String patentId,
            @RequestBody PatchFinalDecisionRequest request
    ) {
        return ApiResponse.ok(patentWorkflowService.patchFinalDecision(patentId, request));
    }

    /**
     * @description AI 평가 레포트 생성 요청 - FastAPI agent 호출 후 상태를 MAIL_READY로 전환.
     */
    @PostMapping("/{patentId}/request-ai-report")
    public ApiResponse<PatentDetailResponse> requestAiReport(@PathVariable String patentId) {
        return ApiResponse.ok(patentWorkflowService.generateAiReport(patentId));
    }

    /**
     * @description 리포트 생성 대기(REVIEW_QUARTER_STARTED) 특허의 AI 평가 레포트를 일괄 생성한다.
     */
    @PostMapping("/batch/request-ai-reports")
    public ApiResponse<BatchAiReportResult> requestAiReports(@Valid @RequestBody BatchPatentIdsRequest request) {
        return ApiResponse.ok(patentWorkflowService.generateAiReportsForWaiting(request.patentIds()));
    }

    /**
     * @description 복수 특허를 MAIL_READY 상태로 일괄 전환하는 API.
     */
    @PostMapping("/batch/mark-mail-ready")
    public ApiResponse<List<String>> markMailReady(@Valid @RequestBody BatchPatentIdsRequest request) {
        return ApiResponse.ok(patentWorkflowService.markMailReady(request.patentIds()));
    }

    /**
     * @description 지연된 사업부 회신 요청의 회신 기한을 연장하고 긴급 요청 시각을 기록한다.
     */
    @PostMapping("/batch/extend-response-deadline")
    public ApiResponse<PatentWorkflowService.WorkflowBatchUpdateResult> extendResponseDeadline(
            @Valid @RequestBody ResponseDeadlineExtensionRequest request
    ) {
        List<PatentWorkflowService.ResponseDeadlineExtensionTarget> targets = request.targets().stream()
                .map(target -> new PatentWorkflowService.ResponseDeadlineExtensionTarget(
                        target.patentId(), target.quarterKey()))
                .toList();
        return ApiResponse.ok(patentWorkflowService.extendBusinessResponseDeadline(
                targets, request.responseDueDate()));
    }
}
