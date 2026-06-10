package com.syuuk.patentflow.patent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.patent.domain.AiReportEditLogEntity;
import com.syuuk.patentflow.patent.domain.PatentReviewHistoryEntity;
import com.syuuk.patentflow.patent.dto.AiEvaluationReportResponse;
import com.syuuk.patentflow.patent.dto.AiReportEditRequest;
import com.syuuk.patentflow.patent.dto.AiReportOverrides;
import com.syuuk.patentflow.patent.dto.EvaluationCategory;
import com.syuuk.patentflow.patent.dto.EvaluationScoreResponse;
import com.syuuk.patentflow.patent.dto.EvidenceDetailResponse;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import com.syuuk.patentflow.patent.dto.Recommendation;
import com.syuuk.patentflow.patent.repository.AiReportEditLogRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * FR-LEGAL-09: AI 레포트 법무 편집 — 오버라이드 분리 저장, 낙관적 락(409), 감사 로그를 검증한다.
 * AI 원본(ai_* 컬럼)은 편집으로 절대 변형되지 않아야 한다.
 */
class AiReportEditServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PatentReviewHistoryRepository historyRepository;
    private AiReportEditLogRepository editLogRepository;
    private PatentReviewService patentReviewService;
    private AiReportEditService service;
    private PatentReviewHistoryEntity history;

    @BeforeEach
    void setUp() {
        historyRepository = mock(PatentReviewHistoryRepository.class);
        editLogRepository = mock(AiReportEditLogRepository.class);
        patentReviewService = mock(PatentReviewService.class);
        service = new AiReportEditService(historyRepository, editLogRepository, patentReviewService, OBJECT_MAPPER);

        history = new PatentReviewHistoryEntity("PAT-001", "2026-Q2");
        history.setAiReportId("REPORT-PAT-001-1");
        history.setAiRecommendation(Recommendation.HOLD);
        when(historyRepository.findByPatentIdOrderByCreatedAtDesc("PAT-001")).thenReturn(List.of(history));
        // record(final)는 mock이 불가하므로 최소 필드만 채운 실제 응답을 돌려준다.
        PatentDetailResponse detail = new PatentDetailResponse(
                "PAT-001", null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, Recommendation.HOLD, null, null, null,
                originalReport(), null, null, true);
        when(patentReviewService.getPatentDetail("PAT-001")).thenReturn(detail);
    }

    private AiReportEditRequest request(int expectedVersion, AiReportOverrides overrides) {
        return new AiReportEditRequest("REPORT-PAT-001-1", expectedVersion, overrides);
    }

    private AiReportOverrides recommendationOverride(Recommendation recommendation, String text) {
        return new AiReportOverrides(recommendation, text, null, null, null, null, null);
    }

    @Test
    void editStoresOverridesSeparatelyAndBumpsVersionAndLogs() {
        service.editAiReport("PAT-001", request(0, recommendationOverride(Recommendation.MAINTAIN, "법무 검토 결과")), "legal01");

        // AI 원본 컬럼은 변형되지 않고 오버라이드만 분리 저장된다.
        assertThat(history.getAiRecommendation()).isEqualTo(Recommendation.HOLD);
        assertThat(history.getAiEditOverridesJson()).contains("MAINTAIN").contains("법무 검토 결과");
        assertThat(history.getAiEditVersion()).isEqualTo(1);
        assertThat(history.getAiEditedBy()).isEqualTo("legal01");
        assertThat(history.getAiEditBaseReportId()).isEqualTo("REPORT-PAT-001-1");

        ArgumentCaptor<AiReportEditLogEntity> log = ArgumentCaptor.forClass(AiReportEditLogEntity.class);
        verify(editLogRepository).save(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo(AiReportEditLogEntity.Action.EDIT);
        assertThat(log.getValue().getEditor()).isEqualTo("legal01");
    }

    @Test
    void partialEditsAccumulateAcrossPatches() {
        service.editAiReport("PAT-001", request(0, recommendationOverride(Recommendation.MAINTAIN, null)), "legal01");
        service.editAiReport("PAT-001", new AiReportEditRequest("REPORT-PAT-001-1", 1,
                new AiReportOverrides(null, null, "핵심 근거 수정", null, null, null, null)), "legal02");

        assertThat(history.getAiEditOverridesJson()).contains("MAINTAIN").contains("핵심 근거 수정");
        assertThat(history.getAiEditVersion()).isEqualTo(2);
        assertThat(history.getAiEditedBy()).isEqualTo("legal02");
    }

    @Test
    void staleBaseReportIdIsRejectedWith409() {
        AiReportEditRequest staleRequest = new AiReportEditRequest("REPORT-PAT-001-OLD", 0,
                recommendationOverride(Recommendation.MAINTAIN, null));

        assertThatThrownBy(() -> service.editAiReport("PAT-001", staleRequest, "legal01"))
                .isInstanceOf(PatentFlowException.class)
                .extracting(e -> ((PatentFlowException) e).errorCode())
                .isEqualTo(ErrorCode.AI_REPORT_EDIT_CONFLICT);
    }

    @Test
    void concurrentEditVersionMismatchIsRejectedWith409() {
        history.setAiEditVersion(3);

        assertThatThrownBy(() -> service.editAiReport("PAT-001",
                request(2, recommendationOverride(Recommendation.MAINTAIN, null)), "legal01"))
                .isInstanceOf(PatentFlowException.class)
                .extracting(e -> ((PatentFlowException) e).errorCode())
                .isEqualTo(ErrorCode.AI_REPORT_EDIT_CONFLICT);
    }

    @Test
    void editAfterFinalDecisionIsBlocked() {
        history.setFinalDecisionId("PAT-001-DEC-01");

        assertThatThrownBy(() -> service.editAiReport("PAT-001",
                request(0, recommendationOverride(Recommendation.MAINTAIN, null)), "legal01"))
                .isInstanceOf(PatentFlowException.class)
                .extracting(e -> ((PatentFlowException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_WORKFLOW_STATUS);
    }

    @Test
    void degradedReportIsEditable() {
        // 부분 생성(degraded) 레포트의 보완이 편집의 핵심 유스케이스다.
        history.setAiDegraded(true);

        service.editAiReport("PAT-001", request(0, recommendationOverride(Recommendation.MAINTAIN, "보완 완료")), "legal01");

        assertThat(history.getAiEditOverridesJson()).contains("보완 완료");
    }

    @Test
    void revertClearsOverridesAndLogsButKeepsVersionMoving() {
        service.editAiReport("PAT-001", request(0, recommendationOverride(Recommendation.MAINTAIN, null)), "legal01");

        service.revertAiReport("PAT-001", "legal02");

        assertThat(history.getAiEditOverridesJson()).isNull();
        assertThat(history.getAiEditedBy()).isNull();
        assertThat(history.getAiEditBaseReportId()).isNull();
        assertThat(history.getAiEditVersion()).isEqualTo(2);

        ArgumentCaptor<AiReportEditLogEntity> log = ArgumentCaptor.forClass(AiReportEditLogEntity.class);
        verify(editLogRepository, org.mockito.Mockito.times(2)).save(log.capture());
        assertThat(log.getAllValues().get(1).getAction()).isEqualTo(AiReportEditLogEntity.Action.REVERT);
        assertThat(log.getAllValues().get(1).getOverridesJson()).contains("MAINTAIN");
    }

    @Test
    void revertWithoutEditsIsRejected() {
        assertThatThrownBy(() -> service.revertAiReport("PAT-001", "legal01"))
                .isInstanceOf(PatentFlowException.class)
                .extracting(e -> ((PatentFlowException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void emptyOverridesAreRejected() {
        assertThatThrownBy(() -> service.editAiReport("PAT-001",
                request(0, new AiReportOverrides(null, null, null, null, null, null, null)), "legal01"))
                .isInstanceOf(PatentFlowException.class)
                .extracting(e -> ((PatentFlowException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void logRegeneratedOverEditOnlyWhenOverridesExist() {
        service.logRegeneratedOverEdit("PAT-001", "SYSTEM");
        verify(editLogRepository, org.mockito.Mockito.never()).save(any());

        service.editAiReport("PAT-001", request(0, recommendationOverride(Recommendation.MAINTAIN, null)), "legal01");
        service.logRegeneratedOverEdit("PAT-001", "SYSTEM");

        ArgumentCaptor<AiReportEditLogEntity> log = ArgumentCaptor.forClass(AiReportEditLogEntity.class);
        verify(editLogRepository, org.mockito.Mockito.times(2)).save(log.capture());
        assertThat(log.getAllValues().get(1).getAction()).isEqualTo(AiReportEditLogEntity.Action.REGENERATED_OVER_EDIT);
    }

    // ── 유효 레포트 합성(AiReportOverridesSupport) ───────────────────────────

    private AiEvaluationReportResponse originalReport() {
        return new AiEvaluationReportResponse(
                "REPORT-PAT-001-1", OffsetDateTime.now(), Recommendation.HOLD, "AI 사유",
                280, 70.0, "B", "유지 권고", false, null,
                List.of(new EvaluationScoreResponse(EvaluationCategory.RIGHTS, 60, "B", "AI 근거",
                                List.of(new EvidenceDetailResponse("출처 텍스트", null))),
                        new EvaluationScoreResponse(EvaluationCategory.MARKET, 75, "B", "시장 근거", List.of())),
                List.of(), "# AI 원본", null, "핵심 근거", List.of("판단 1"), List.of("확인 요청 1"), List.of());
    }

    @Test
    void applyOverridesMergesOnlyPresentFieldsAndKeepsEvidenceDetails() {
        history.setAiEditOverridesJson(null);
        AiReportOverrides overrides = new AiReportOverrides(
                Recommendation.MAINTAIN, null, null, null, null,
                List.of(new AiReportOverrides.ScoreOverride(EvaluationCategory.RIGHTS, 72, "A", "법무 수정 근거")),
                null);
        history.setAiEditVersion(1);
        history.setAiEditedBy("legal01");
        history.setAiEditedAt(OffsetDateTime.now());
        history.setAiEditBaseReportId("REPORT-PAT-001-1");

        AiEvaluationReportResponse effective = AiReportOverridesSupport.applyOverrides(originalReport(), overrides, history);

        assertThat(effective.edited()).isTrue();
        assertThat(effective.editStale()).isFalse();
        assertThat(effective.recommendation()).isEqualTo(Recommendation.MAINTAIN);
        assertThat(effective.recommendationReason()).isEqualTo("AI 사유"); // 미수정 필드는 원본 유지
        assertThat(effective.rawMarkdown()).isEqualTo("# AI 원본");
        EvaluationScoreResponse rights = effective.scores().get(0);
        assertThat(rights.score()).isEqualTo(72);
        assertThat(rights.grade()).isEqualTo("A");
        assertThat(rights.evidence()).isEqualTo("법무 수정 근거");
        assertThat(rights.evidenceDetails()).hasSize(1); // 클릭형 출처는 AI 소유 — 보존
        assertThat(effective.scores().get(1).score()).isEqualTo(75); // 미수정 축 유지
    }

    @Test
    void applyOverridesMarksStaleWhenReportRegeneratedAfterEdit() {
        history.setAiReportId("REPORT-PAT-001-2"); // 편집 후 재생성됨
        history.setAiEditBaseReportId("REPORT-PAT-001-1");
        history.setAiEditVersion(1);

        AiEvaluationReportResponse effective = AiReportOverridesSupport.applyOverrides(
                originalReport(), recommendationOverride(Recommendation.MAINTAIN, null), history);

        assertThat(effective.editStale()).isTrue();
    }

    @Test
    void noOverridesStillExposesEditVersionToken() {
        history.setAiEditVersion(4);

        AiEvaluationReportResponse effective = AiReportOverridesSupport.applyOverrides(originalReport(), null, history);

        assertThat(effective.edited()).isFalse();
        assertThat(effective.editVersion()).isEqualTo(4);
    }
}
