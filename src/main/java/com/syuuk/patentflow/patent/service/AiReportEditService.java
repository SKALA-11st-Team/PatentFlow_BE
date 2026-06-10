package com.syuuk.patentflow.patent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.patent.domain.AiReportEditLogEntity;
import com.syuuk.patentflow.patent.domain.PatentReviewHistoryEntity;
import com.syuuk.patentflow.patent.dto.AiEvaluationReportResponse;
import com.syuuk.patentflow.patent.dto.AiReportEditRequest;
import com.syuuk.patentflow.patent.dto.AiReportOverrides;
import com.syuuk.patentflow.patent.repository.AiReportEditLogRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 평가 레포트 법무 편집(FR-LEGAL-09).
 *
 * AI 원본(ai_* 컬럼)은 불변으로 두고 편집은 오버라이드 JSON으로 분리 저장한다.
 * 모든 편집/되돌리기는 append-only 감사 로그(ai_report_edit_logs)에 남는다.
 *
 * 편집 가능 조건:
 * - AI 레포트가 존재해야 한다(생성 전 편집 불가).
 * - 최종 결정 기록 전이어야 한다(결정은 당시 레포트를 근거로 하므로 사후 변경 차단 — 409).
 * - degraded 레포트는 편집 허용(부분 생성 레포트의 보완이 핵심 유스케이스).
 * - 사업부 회신 후에도 편집 허용 — 사업부가 본 내용은 business_ai_report_snapshot_json으로 보존된다.
 */
@Service
public class AiReportEditService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PatentReviewHistoryRepository reviewHistoryRepository;
    private final AiReportEditLogRepository editLogRepository;
    private final PatentReviewService patentReviewService;
    private final ObjectMapper objectMapper;

    public AiReportEditService(
            PatentReviewHistoryRepository reviewHistoryRepository,
            AiReportEditLogRepository editLogRepository,
            @Lazy PatentReviewService patentReviewService,
            ObjectMapper objectMapper
    ) {
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.editLogRepository = editLogRepository;
        this.patentReviewService = patentReviewService;
        this.objectMapper = objectMapper;
    }

    /**
     * @relatedFR FR-LEGAL-09
     * @description 레포트 오버라이드를 부분 PATCH로 누적 저장하고 유효 레포트를 돌려준다.
     */
    @Transactional
    public AiEvaluationReportResponse editAiReport(String patentId, AiReportEditRequest request, String actor) {
        PatentReviewHistoryEntity history = latestHistoryOrThrow(patentId);
        validateEditable(history);
        validateNotConflicting(history, request.baseReportId(), request.expectedEditVersion());
        if (request.overrides() == null || request.overrides().isEmpty()) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "수정할 항목이 없습니다.");
        }

        AiReportOverrides current = AiReportOverridesSupport.readOverrides(objectMapper, history.getAiEditOverridesJson());
        AiReportOverrides merged = current == null ? request.overrides() : current.mergedWith(request.overrides());
        OffsetDateTime now = OffsetDateTime.now(KST);
        history.setAiEditOverridesJson(writeJson(merged));
        history.setAiEditedBy(actor);
        history.setAiEditedAt(now);
        history.setAiEditVersion(currentEditVersion(history) + 1);
        history.setAiEditBaseReportId(history.getAiReportId());
        reviewHistoryRepository.save(history);
        editLogRepository.save(new AiReportEditLogEntity(
                patentId, history.getQuarterKey(), actor, now,
                AiReportEditLogEntity.Action.EDIT, history.getAiEditOverridesJson(), history.getAiReportId()));
        return patentReviewService.getPatentDetail(patentId).aiEvaluationReport();
    }

    /**
     * @relatedFR FR-LEGAL-09
     * @description 편집을 모두 폐기하고 AI 원본으로 되돌린다.
     */
    @Transactional
    public AiEvaluationReportResponse revertAiReport(String patentId, String actor) {
        PatentReviewHistoryEntity history = latestHistoryOrThrow(patentId);
        validateEditable(history);
        if (history.getAiEditOverridesJson() == null) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "되돌릴 편집 내역이 없습니다.");
        }
        String previousOverrides = history.getAiEditOverridesJson();
        OffsetDateTime now = OffsetDateTime.now(KST);
        history.setAiEditOverridesJson(null);
        history.setAiEditedBy(null);
        history.setAiEditedAt(null);
        // 버전은 계속 증가시켜 동시 편집 화면이 stale 버전으로 저장하는 것을 막는다.
        history.setAiEditVersion(currentEditVersion(history) + 1);
        history.setAiEditBaseReportId(null);
        reviewHistoryRepository.save(history);
        editLogRepository.save(new AiReportEditLogEntity(
                patentId, history.getQuarterKey(), actor, now,
                AiReportEditLogEntity.Action.REVERT, previousOverrides, history.getAiReportId()));
        return patentReviewService.getPatentDetail(patentId).aiEvaluationReport();
    }

    /** 레포트 재생성이 기존 편집 위에서 일어났음을 감사 로그에 남긴다(편집은 보존, stale 처리). */
    @Transactional
    public void logRegeneratedOverEdit(String patentId, String actor) {
        PatentReviewHistoryEntity history = reviewHistoryRepository
                .findByPatentIdOrderByCreatedAtDesc(patentId).stream().findFirst().orElse(null);
        if (history == null || history.getAiEditOverridesJson() == null) {
            return;
        }
        editLogRepository.save(new AiReportEditLogEntity(
                patentId, history.getQuarterKey(), actor, OffsetDateTime.now(KST),
                AiReportEditLogEntity.Action.REGENERATED_OVER_EDIT,
                history.getAiEditOverridesJson(), history.getAiReportId()));
    }

    private PatentReviewHistoryEntity latestHistoryOrThrow(String patentId) {
        patentReviewService.ensurePatentExists(patentId);
        return reviewHistoryRepository.findByPatentIdOrderByCreatedAtDesc(patentId).stream()
                .findFirst()
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "검토 이력이 없는 특허는 레포트를 편집할 수 없습니다."));
    }

    private void validateEditable(PatentReviewHistoryEntity history) {
        if (history.getAiReportId() == null) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "AI 레포트가 아직 생성되지 않아 편집할 수 없습니다.");
        }
        if (history.getFinalDecisionId() != null) {
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS,
                    "최종 판단이 기록된 레포트는 편집할 수 없습니다.");
        }
    }

    private void validateNotConflicting(PatentReviewHistoryEntity history, String baseReportId, int expectedEditVersion) {
        if (!history.getAiReportId().equals(baseReportId)) {
            throw new PatentFlowException(ErrorCode.AI_REPORT_EDIT_CONFLICT,
                    "편집 도중 AI 레포트가 재생성되었습니다. 새 레포트를 확인한 뒤 다시 편집해주세요.");
        }
        if (currentEditVersion(history) != expectedEditVersion) {
            throw new PatentFlowException(ErrorCode.AI_REPORT_EDIT_CONFLICT,
                    "다른 관리자가 먼저 레포트를 수정했습니다. 새로고침 후 다시 시도해주세요.");
        }
    }

    private int currentEditVersion(PatentReviewHistoryEntity history) {
        return history.getAiEditVersion() == null ? 0 : history.getAiEditVersion();
    }

    private String writeJson(AiReportOverrides overrides) {
        try {
            return objectMapper.writeValueAsString(overrides);
        } catch (Exception exception) {
            throw new IllegalStateException("AI 레포트 편집 내용을 저장할 수 없습니다.", exception);
        }
    }
}
