package com.syuuk.patentflow.patent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.patent.client.AiReportAgentClient;
import com.syuuk.patentflow.patent.dto.BusinessOpinionResponse;
import com.syuuk.patentflow.patent.dto.FinalDecisionRecordResponse;
import com.syuuk.patentflow.patent.dto.FinalDecisionResponse;
import com.syuuk.patentflow.patent.dto.LegalActionResult;
import com.syuuk.patentflow.patent.dto.PatchFinalDecisionRequest;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import com.syuuk.patentflow.patent.dto.PatentLifecycleStatus;
import com.syuuk.patentflow.patent.dto.PatentSummaryResponse;
import com.syuuk.patentflow.patent.dto.Recommendation;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * be-patent-core-3: FR-LEGAL-20용 patchFinalDecision은 '이미 기록된 최종 판단'(LEGAL_ACTION_RECORDED)의
 * 수정/취소 전용이다. 비정상 단계에서 PATCH로 결정 레코드를 강제 생성하거나 상태를 점프/되돌리지 못하도록
 * 진입부 워크플로우 상태 가드를 락한다.
 */
class PatentWorkflowServicePatchFinalDecisionTest {

    private PatentReviewService patentReviewService;
    private PatentReviewHistoryRepository reviewHistoryRepository;
    private AnnualFeeScheduleService annualFeeScheduleService;
    private PatentWorkflowService service;

    @BeforeEach
    void setUp() {
        patentReviewService = mock(PatentReviewService.class);
        reviewHistoryRepository = mock(PatentReviewHistoryRepository.class);
        annualFeeScheduleService = mock(AnnualFeeScheduleService.class);
        service = new PatentWorkflowService(
                patentReviewService,
                mock(PatentMetadataRepository.class),
                reviewHistoryRepository,
                mock(AiReportAgentClient.class),
                annualFeeScheduleService,
                mock(AiReportEditService.class),
                mock(com.syuuk.patentflow.settings.service.ValuationCriteriaService.class),
                new ObjectMapper(),
                mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    @SuppressWarnings("unchecked")
    private void stubUpdateApplyingTransform(PatentDetailResponse patent) {
        when(patentReviewService.updatePatentInternal(eq(patent.patentId()), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<PatentDetailResponse, PatentDetailResponse> fn = invocation.getArgument(1);
                    return fn.apply(patent);
                });
    }

    @Test
    void patchOnNonLegalActionRecordedStateIsRejected() {
        PatentDetailResponse patent = patentAt(ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED, null);
        stubUpdateApplyingTransform(patent);

        assertThatThrownBy(() -> service.patchFinalDecision(
                patent.patentId(), new PatchFinalDecisionRequest(LegalActionResult.MAINTAINED, "수정"), "ADMIN"))
                .isInstanceOf(PatentFlowException.class)
                .extracting(e -> ((PatentFlowException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_WORKFLOW_STATUS);
    }

    @Test
    void patchOnNotInReviewStateIsRejected() {
        PatentDetailResponse patent = patentAt(ReviewWorkflowStatus.NOT_IN_REVIEW, null);
        stubUpdateApplyingTransform(patent);

        assertThatThrownBy(() -> service.patchFinalDecision(
                patent.patentId(), new PatchFinalDecisionRequest(null, null), "ADMIN"))
                .isInstanceOf(PatentFlowException.class)
                .extracting(e -> ((PatentFlowException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_WORKFLOW_STATUS);
    }

    @Test
    void patchModifyOnLegalActionRecordedStaysRecorded() {
        PatentDetailResponse patent = patentAt(ReviewWorkflowStatus.LEGAL_ACTION_RECORDED, LegalActionResult.ABANDONED);
        stubUpdateApplyingTransform(patent);

        FinalDecisionResponse response = service.patchFinalDecision(
                patent.patentId(), new PatchFinalDecisionRequest(LegalActionResult.ABANDONED, "사유 정정"), "ADMIN");

        assertThat(response.reviewWorkflowStatus()).isEqualTo(ReviewWorkflowStatus.LEGAL_ACTION_RECORDED);
        assertThat(response.legalActionResult()).isEqualTo(LegalActionResult.ABANDONED);
    }

    @Test
    void patchClearOnLegalActionRecordedReturnsToBusinessResponseReceived() {
        PatentDetailResponse patent = patentAt(ReviewWorkflowStatus.LEGAL_ACTION_RECORDED, LegalActionResult.ABANDONED);
        stubUpdateApplyingTransform(patent);

        FinalDecisionResponse response = service.patchFinalDecision(
                patent.patentId(), new PatchFinalDecisionRequest(null, null), "ADMIN");

        // 취소(cleared)는 중앙 전이표(LEGAL_ACTION_RECORDED→BUSINESS_RESPONSE_RECEIVED)를 경유해 복귀한다.
        assertThat(response.reviewWorkflowStatus()).isEqualTo(ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED);
        assertThat(response.legalActionResult()).isNull();
    }

    private PatentDetailResponse patentAt(ReviewWorkflowStatus status, LegalActionResult legalActionResult) {
        return new PatentDetailResponse(
                "PAT-PATCH",
                "P-PATCH",
                "10-2026-0000009",
                null,
                "테스트 특허",
                "테스트 특허",
                "AI",
                "문서처리",
                "PatentFlow",
                "KR",
                "없음",
                LocalDate.parse("2024-01-01"),
                LocalDate.parse("2025-01-01"),
                LocalDate.parse("2044-01-01"),
                "DEPT-AI",
                "AI사업부",
                PatentLifecycleStatus.ACTIVE,
                status,
                LocalDate.parse("2026-06-30"),
                "연차료 납부 검토 시점 도래",
                Recommendation.CONDITIONAL_MAINTAIN,
                null,
                legalActionResult,
                new PatentSummaryResponse("작성 필요", "작성 필요", List.of(), "작성 필요", List.of()),
                null,
                new FinalDecisionRecordResponse("PAT-PATCH-DEC-01", "최초 사유", null, "ADMIN"),
                new BusinessOpinionResponse(null, null, null),
                status != ReviewWorkflowStatus.NOT_IN_REVIEW
                        && status != ReviewWorkflowStatus.LEGAL_ACTION_RECORDED,
                false,
                null);
    }
}
