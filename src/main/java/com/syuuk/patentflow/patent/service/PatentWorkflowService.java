package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.patent.client.AiReportAgentClient;
import com.syuuk.patentflow.patent.client.AiReportAgentClient.AgentEvaluateResponse;
import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import com.syuuk.patentflow.patent.domain.PatentReviewHistoryEntity;
import com.syuuk.patentflow.patent.dto.AiEvaluationReportResponse;
import com.syuuk.patentflow.patent.dto.BatchAiReportResult;
import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import com.syuuk.patentflow.patent.dto.BusinessOpinionResponse;
import com.syuuk.patentflow.patent.dto.EvaluationCategory;
import com.syuuk.patentflow.patent.dto.EvaluationScoreResponse;
import com.syuuk.patentflow.patent.dto.FinalDecisionRecordResponse;
import com.syuuk.patentflow.patent.dto.FinalDecisionRequest;
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
import com.syuuk.patentflow.settings.repository.QuarterSettingRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * 특허 검토 워크플로우 상태 전이 서비스.
 *
 * - AI 레포트 생성, 메일 발송 처리, 사업부 의견 접수, 최종 결정 등 상태 변경 작업을 담당한다.
 * - PatentReviewService는 조회/상태 로딩, 이 서비스는 상태 전이를 맡아 의존 방향을 단방향으로 유지한다.
 * - with*() 메서드는 PatentDetailResponse를 순수하게 변환(immutable transform)하는 빌더 역할이다.
 */
@Service
public class PatentWorkflowService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PatentReviewService patentReviewService;
    private final PatentMetadataRepository patentMetadataRepository;
    private final PatentReviewHistoryRepository reviewHistoryRepository;
    private final AiReportAgentClient aiReportAgentClient;
    private final AiReportStorageService aiReportStorageService;
    private final AnnualFeeScheduleService annualFeeScheduleService;
    private final QuarterSettingRepository quarterSettingRepository;

    public record WorkflowBatchUpdateResult(
            List<String> updatedPatentIds,
            List<String> skippedPatentIds
    ) {}

    public PatentWorkflowService(
            PatentReviewService patentReviewService,
            PatentMetadataRepository patentMetadataRepository,
            PatentReviewHistoryRepository reviewHistoryRepository,
            AiReportAgentClient aiReportAgentClient,
            AiReportStorageService aiReportStorageService,
            AnnualFeeScheduleService annualFeeScheduleService,
            QuarterSettingRepository quarterSettingRepository
    ) {
        this.patentReviewService = patentReviewService;
        this.patentMetadataRepository = patentMetadataRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.aiReportAgentClient = aiReportAgentClient;
        this.aiReportStorageService = aiReportStorageService;
        this.annualFeeScheduleService = annualFeeScheduleService;
        this.quarterSettingRepository = quarterSettingRepository;
    }

    // ── AI 레포트 생성 ────────────────────────────────────────

    public PatentDetailResponse generateAiReport(String patentId) {
        PatentDetailResponse patent = patentReviewService.findPatent(patentId);
        if (patent.reviewWorkflowStatus() != ReviewWorkflowStatus.REVIEW_QUARTER_STARTED) {
            System.err.println("DEBUG: patentId=" + patentId + ", status=" + patent.reviewWorkflowStatus());
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS,
                    "AI 레포트는 검토 시작(REVIEW_QUARTER_STARTED) 상태에서만 생성할 수 있습니다.");
        }
        AgentEvaluateResponse agentResponse = aiReportAgentClient.evaluate(patentId);
        AiEvaluationReportResponse report = mapAgentResponse(agentResponse, patentId);
        return patentReviewService.updatePatentInternal(patentId, p -> withAiReport(p, report, agentResponse.summaryText()));
    }

    // 배치 자동 생성 전용 — evaluateForBatch(20분 타임아웃)로 장시간 실행 허용
    public PatentDetailResponse generateAiReportForBatch(String patentId) {
        PatentDetailResponse patent = patentReviewService.findPatent(patentId);
        if (patent.reviewWorkflowStatus() != ReviewWorkflowStatus.REVIEW_QUARTER_STARTED) {
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS,
                    "AI 레포트는 검토 시작(REVIEW_QUARTER_STARTED) 상태에서만 생성할 수 있습니다.");
        }
        AgentEvaluateResponse agentResponse = aiReportAgentClient.evaluateForBatch(patentId);
        AiEvaluationReportResponse report = mapAgentResponse(agentResponse, patentId);
        return patentReviewService.updatePatentInternal(patentId, p -> withAiReport(p, report, agentResponse.summaryText()));
    }

    public BatchAiReportResult generateAiReportsForWaiting(List<String> patentIds) {
        List<String> generated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        if (patentIds == null || patentIds.isEmpty()) {
            return new BatchAiReportResult(0, 0, 0, 0, List.of(), List.of(), List.of());
        }

        for (String patentId : new LinkedHashSet<>(patentIds)) {
            PatentDetailResponse patent = patentReviewService.findPatentOrNull(patentId);
            if (patent == null || patent.reviewWorkflowStatus() != ReviewWorkflowStatus.REVIEW_QUARTER_STARTED) {
                skipped.add(patentId);
                continue;
            }
            try {
                generateAiReportForBatch(patentId);
                generated.add(patentId);
            } catch (Exception e) {
                failed.add(patentId);
            }
        }

        return new BatchAiReportResult(
                new LinkedHashSet<>(patentIds).size(),
                generated.size(),
                skipped.size(),
                failed.size(),
                generated,
                skipped,
                failed);
    }

    // ── 메일 발송 처리 ────────────────────────────────────────

    public List<String> markMailReady(List<String> patentIds) {
        List<String> updated = new ArrayList<>();
        for (String patentId : patentIds) {
            try {
                PatentDetailResponse patent = patentReviewService.findPatent(patentId);
                if (patent.reviewWorkflowStatus() == ReviewWorkflowStatus.MAIL_READY) {
                    updated.add(patentId);
                }
            } catch (Exception ignored) {}
        }
        return updated;
    }

    public WorkflowBatchUpdateResult markMailingSent(List<String> patentIds) {
        List<String> updatedPatentIds = new ArrayList<>();
        List<String> skippedPatentIds = new ArrayList<>();

        for (String patentId : new LinkedHashSet<>(patentIds)) {
            PatentDetailResponse patent = patentReviewService.findPatentOrNull(patentId);
            if (patent == null || patent.reviewWorkflowStatus() != ReviewWorkflowStatus.MAIL_READY) {
                skippedPatentIds.add(patentId);
                continue;
            }
            PatentDetailResponse updated = patentReviewService.updatePatentInternal(
                    patentId, p -> withReviewWorkflowStatus(p, ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE));
            setDefaultResponseDueDate(updated.patentId());
            updatedPatentIds.add(updated.patentId());
        }

        return new WorkflowBatchUpdateResult(updatedPatentIds, skippedPatentIds);
    }

    public WorkflowBatchUpdateResult extendBusinessResponseDeadline(
            List<ResponseDeadlineExtensionTarget> targets,
            LocalDate responseDueDate
    ) {
        if (targets == null || targets.isEmpty() || responseDueDate == null) {
            return new WorkflowBatchUpdateResult(List.of(), List.of());
        }

        List<String> updatedPatentIds = new ArrayList<>();
        List<String> skippedPatentIds = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now(KST);

        for (ResponseDeadlineExtensionTarget target : targets) {
            if (target == null || target.patentId() == null || target.patentId().isBlank()
                    || target.quarterKey() == null || target.quarterKey().isBlank()) {
                continue;
            }

            PatentReviewHistoryEntity history = reviewHistoryRepository
                    .findByPatentIdAndQuarterKey(target.patentId(), target.quarterKey())
                    .orElse(null);
            if (history == null || history.getBusinessOpinionDecision() != null
                    || !canUrgentlyRequestBusinessResponse(history.getReviewWorkflowStatus())) {
                skippedPatentIds.add(target.patentId());
                continue;
            }

            history.setReviewWorkflowStatus(ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE);
            if (history.getResponseDueDate() == null) {
                history.setResponseDueDate(resolveQuarterResponseDueDate(target.quarterKey()));
            }
            history.setResponseDueDateExtendedUntil(responseDueDate);
            history.setUrgentRequestedAt(now);
            history.setDelayed(true);
            reviewHistoryRepository.save(history);
            updatedPatentIds.add(target.patentId());
        }

        return new WorkflowBatchUpdateResult(updatedPatentIds, skippedPatentIds);
    }

    private boolean canUrgentlyRequestBusinessResponse(ReviewWorkflowStatus status) {
        return status == ReviewWorkflowStatus.MAIL_READY
                || status == ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE;
    }

    public WorkflowBatchUpdateResult markMailingRetryRequired(List<String> patentIds) {
        List<String> updatedPatentIds = new ArrayList<>();
        List<String> skippedPatentIds = new ArrayList<>();

        for (String patentId : new LinkedHashSet<>(patentIds)) {
            PatentDetailResponse patent = patentReviewService.findPatentOrNull(patentId);
            if (patent == null || patent.reviewWorkflowStatus() != ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE) {
                skippedPatentIds.add(patentId);
                continue;
            }
            PatentDetailResponse updated = patentReviewService.updatePatentInternal(
                    patentId, p -> withReviewWorkflowStatus(p, ReviewWorkflowStatus.MAIL_READY));
            updatedPatentIds.add(updated.patentId());
        }

        return new WorkflowBatchUpdateResult(updatedPatentIds, skippedPatentIds);
    }

    // ── 사업부 의견 / 최종 결정 ───────────────────────────────

    public void recordBusinessOpinion(
            String patentId,
            BusinessOpinionDecision decision,
            String reason,
            OffsetDateTime submittedAt
    ) {
        patentReviewService.updatePatentInternal(patentId,
                patent -> withBusinessOpinion(patent, decision, reason, submittedAt));
    }

    public FinalDecisionResponse patchFinalDecision(String patentId, PatchFinalDecisionRequest request) {
        OffsetDateTime decidedAt = OffsetDateTime.now(KST);
        PatentDetailResponse updated = patentReviewService.updatePatentInternal(patentId, patent -> {
            if (request.legalActionResult() == null && request.reason() == null) {
                return withClearedFinalDecision(patent);
            }
            return withPatchedFinalDecision(patent, request, decidedAt);
        });
        return new FinalDecisionResponse(updated.patentId(), updated.finalDecisionRecord(),
                updated.legalActionResult(), updated.reviewWorkflowStatus());
    }

    public FinalDecisionResponse recordFinalDecision(String patentId, FinalDecisionRequest request) {
        OffsetDateTime decidedAt = OffsetDateTime.now(KST);
        PatentDetailResponse updated = patentReviewService.updatePatentInternal(patentId, patent -> {
            if (!canRecordFinalDecision(patent.reviewWorkflowStatus())) {
                throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS);
            }
            return withFinalDecision(patent, request, decidedAt);
        });
        return new FinalDecisionResponse(updated.patentId(), updated.finalDecisionRecord(),
                updated.legalActionResult(), updated.reviewWorkflowStatus());
    }

    public FinalDecisionResponse recordDelayedFinalDecision(String patentId, String quarterKey, FinalDecisionRequest request) {
        PatentReviewHistoryEntity history = reviewHistoryRepository
                .findByPatentIdAndQuarterKey(patentId, quarterKey)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.PATENT_NOT_FOUND));
        if (!history.isDelayed()) {
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS);
        }

        OffsetDateTime decidedAt = OffsetDateTime.now(KST);
        history.setReviewWorkflowStatus(ReviewWorkflowStatus.NOT_IN_REVIEW);
        history.setLegalActionResult(request.legalActionResult());
        history.setFinalDecisionId(history.getFinalDecisionId() == null
                ? patentId + "-" + quarterKey + "-DEC-01"
                : history.getFinalDecisionId());
        history.setFinalDecisionReason(request.reason());
        history.setFinalDecisionDecidedAt(decidedAt);
        history.setDelayed(false);
        reviewHistoryRepository.save(history);

        patentMetadataRepository.findById(patentId).ifPresent(entity -> {
            entity.setInReview(false);
            if (request.legalActionResult() == LegalActionResult.ABANDONED) {
                entity.setPatentStatus(PatentLifecycleStatus.ABANDONED);
            } else if (request.legalActionResult() == LegalActionResult.MAINTAINED && history.getAnnualFeeDueDate() != null) {
                entity.setFeeDueDate(annualFeeScheduleService.advanceAfterMaintenance(
                        entity.getCountry(), history.getAnnualFeeDueDate(), entity.getExpectedExpirationDate()));
            }
            patentMetadataRepository.save(entity);
        });

        return new FinalDecisionResponse(
                patentId,
                new FinalDecisionRecordResponse(
                        history.getFinalDecisionId(),
                        history.getFinalDecisionReason(),
                        history.getFinalDecisionDecidedAt()),
                history.getLegalActionResult(),
                history.getReviewWorkflowStatus());
    }

    // ── 분기 / 배치 관리 ─────────────────────────────────────

    public void bulkUpdateWorkflowStatus(List<String> patentIds, ReviewWorkflowStatus newStatus, String quarterKey) {
        if (patentIds == null || patentIds.isEmpty()) return;
        for (String patentId : patentIds) {
            PatentReviewHistoryEntity history = reviewHistoryRepository
                    .findByPatentIdAndQuarterKey(patentId, quarterKey)
                    .orElseGet(() -> new PatentReviewHistoryEntity(patentId, quarterKey));
            history.setReviewWorkflowStatus(newStatus);
            reviewHistoryRepository.save(history);
        }
    }

    public List<String> createQuarterReviewTargets(
            String quarterKey,
            LocalDate paymentPeriodStart,
            LocalDate paymentPeriodEnd
    ) {
        List<String> reviewStarted = new ArrayList<>();
        patentMetadataRepository.findAll(Sort.by("patentId")).forEach(entity -> {
            if (entity.getPatentStatus() != PatentLifecycleStatus.ACTIVE) {
                return;
            }
            LocalDate dueDate = entity.getFeeDueDate() != null
                    ? entity.getFeeDueDate()
                    : annualFeeScheduleService.calculateNextDueDate(
                            entity.getCountry(), entity.getApplicationDate(),
                            entity.getRegistrationDate(), entity.getExpectedExpirationDate());
            if (entity.getFeeDueDate() == null && dueDate != null) {
                entity.setFeeDueDate(dueDate);
            }
            // 납부 기간 범위를 벗어난 특허는 저장만 하고 검토 제외
            if (dueDate == null || dueDate.isBefore(paymentPeriodStart) || dueDate.isAfter(paymentPeriodEnd)) {
                patentMetadataRepository.save(entity);
                return;
            }
            entity.setInReview(true);
            entity.setCurrentQuarterKey(quarterKey);
            patentMetadataRepository.save(entity);

            PatentReviewHistoryEntity history = reviewHistoryRepository
                    .findByPatentIdAndQuarterKey(entity.getPatentId(), quarterKey)
                    .orElseGet(() -> new PatentReviewHistoryEntity(entity.getPatentId(), quarterKey));
            history.setReviewWorkflowStatus(ReviewWorkflowStatus.REVIEW_QUARTER_STARTED);
            history.setResponseDueDate(null);
            history.setResponseDueDateExtendedUntil(null);
            history.setUrgentRequestedAt(null);
            if (history.getAiRecommendation() == null) {
                history.setAiRecommendation(Recommendation.HOLD);
            }
            history.setAnnualFeeDueDate(dueDate);
            history.setDepartmentId(patentReviewService.departmentId(entity.getBusinessArea()));
            history.setDepartmentName(patentReviewService.departmentName(entity.getBusinessArea()));
            reviewHistoryRepository.save(history);
            reviewStarted.add(entity.getPatentId());
        });
        return reviewStarted;
    }

    private void setDefaultResponseDueDate(String patentId) {
        String quarterKey = patentMetadataRepository.findById(patentId)
                .map(PatentMetadataEntity::getCurrentQuarterKey)
                .orElse(null);
        if (quarterKey == null || quarterKey.isBlank()) {
            return;
        }
        PatentReviewHistoryEntity history = reviewHistoryRepository
                .findByPatentIdAndQuarterKey(patentId, quarterKey)
                .orElse(null);
        if (history == null || history.getResponseDueDate() != null) {
            return;
        }
        history.setResponseDueDate(resolveQuarterResponseDueDate(quarterKey));
        reviewHistoryRepository.save(history);
    }

    private LocalDate resolveQuarterResponseDueDate(String quarterKey) {
        return quarterSettingRepository.findById(quarterKey)
                .map(q -> q.getSubmissionDeadline() != null ? q.getSubmissionDeadline() : q.getStartDate())
                .orElse(null);
    }

    public record ResponseDeadlineExtensionTarget(String patentId, String quarterKey) {}

    // ── 상태 변환 빌더 (with*) ────────────────────────────────

    PatentDetailResponse withAiReport(PatentDetailResponse patent, AiEvaluationReportResponse report, String agentSummary) {
        return new PatentDetailResponse(
                patent.patentId(), patent.managementNumber(), patent.applicationNumber(),
                patent.registrationNumber(), patent.title(), patent.draftTitle(),
                patent.businessArea(), patent.technologyArea(), patent.productName(),
                patent.country(), patent.coApplicants(), patent.applicationDate(),
                patent.registrationDate(), patent.expectedExpirationDate(),
                patent.departmentId(), patent.departmentName(), patent.lifecycleStatus(),
                ReviewWorkflowStatus.MAIL_READY, patent.feeDueDate(), patent.reviewReason(),
                report.recommendation(), patent.businessOpinionDecision(), patent.legalActionResult(),
                withAgentSummary(patent.summary(), agentSummary), report,
                patent.finalDecisionRecord(), patent.businessOpinion(),
                true,
                patent.isDelayed(),
                patent.responseDueDate(),
                patent.responseDueDateExtendedUntil(),
                patent.urgentRequestedAt(),
                patent.currentQuarterKey());
    }

    private PatentSummaryResponse withAgentSummary(PatentSummaryResponse summary, String agentSummary) {
        if (agentSummary == null || agentSummary.isBlank()) return summary;
        return new PatentSummaryResponse(agentSummary, summary.problemSolved(),
                summary.coreTechnicalPoints(), summary.claimsSummary(), summary.missingFields());
    }

    private PatentDetailResponse withBusinessOpinion(
            PatentDetailResponse patent, BusinessOpinionDecision decision,
            String reason, OffsetDateTime submittedAt
    ) {
        return new PatentDetailResponse(
                patent.patentId(), patent.managementNumber(), patent.applicationNumber(),
                patent.registrationNumber(), patent.title(), patent.draftTitle(),
                patent.businessArea(), patent.technologyArea(), patent.productName(),
                patent.country(), patent.coApplicants(), patent.applicationDate(),
                patent.registrationDate(), patent.expectedExpirationDate(),
                patent.departmentId(), patent.departmentName(), patent.lifecycleStatus(),
                ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED, patent.feeDueDate(),
                patent.reviewReason(), patent.currentRecommendation(), decision,
                patent.legalActionResult(), patent.summary(), patent.aiEvaluationReport(),
                patent.finalDecisionRecord(), new BusinessOpinionResponse(decision, reason, submittedAt),
                true,
                patent.isDelayed(),
                patent.responseDueDate(),
                patent.responseDueDateExtendedUntil(),
                patent.urgentRequestedAt(),
                patent.currentQuarterKey());
    }

    private PatentDetailResponse withFinalDecision(
            PatentDetailResponse patent, FinalDecisionRequest request, OffsetDateTime decidedAt
    ) {
        LocalDate newDueDate = request.legalActionResult() == LegalActionResult.MAINTAINED
                ? annualFeeScheduleService.advanceAfterMaintenance(
                        patent.country(), patent.feeDueDate(), patent.expectedExpirationDate())
                : patent.feeDueDate();
        return new PatentDetailResponse(
                patent.patentId(), patent.managementNumber(), patent.applicationNumber(),
                patent.registrationNumber(), patent.title(), patent.draftTitle(),
                patent.businessArea(), patent.technologyArea(), patent.productName(),
                patent.country(), patent.coApplicants(), patent.applicationDate(),
                patent.registrationDate(), patent.expectedExpirationDate(),
                patent.departmentId(), patent.departmentName(),
                lifecycleStatusByLegalAction(request.legalActionResult()),
                ReviewWorkflowStatus.NOT_IN_REVIEW, newDueDate, patent.reviewReason(),
                patent.currentRecommendation(), patent.businessOpinionDecision(),
                request.legalActionResult(), patent.summary(), patent.aiEvaluationReport(),
                new FinalDecisionRecordResponse(
                        patent.finalDecisionRecord().decisionId() == null
                                ? patent.patentId() + "-DEC-01"
                                : patent.finalDecisionRecord().decisionId(),
                        request.reason(), decidedAt),
                patent.businessOpinion(),
                false,
                false,
                patent.responseDueDate(),
                patent.responseDueDateExtendedUntil(),
                patent.urgentRequestedAt(),
                null);
    }

    private PatentDetailResponse withClearedFinalDecision(PatentDetailResponse patent) {
        return new PatentDetailResponse(
                patent.patentId(), patent.managementNumber(), patent.applicationNumber(),
                patent.registrationNumber(), patent.title(), patent.draftTitle(),
                patent.businessArea(), patent.technologyArea(), patent.productName(),
                patent.country(), patent.coApplicants(), patent.applicationDate(),
                patent.registrationDate(), patent.expectedExpirationDate(),
                patent.departmentId(), patent.departmentName(), patent.lifecycleStatus(),
                ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED, patent.feeDueDate(),
                patent.reviewReason(), patent.currentRecommendation(), patent.businessOpinionDecision(),
                null, patent.summary(), patent.aiEvaluationReport(),
                new FinalDecisionRecordResponse(null, null, null), patent.businessOpinion(),
                true,
                patent.isDelayed(),
                patent.responseDueDate(),
                patent.responseDueDateExtendedUntil(),
                patent.urgentRequestedAt(),
                patent.currentQuarterKey());
    }

    private PatentDetailResponse withPatchedFinalDecision(
            PatentDetailResponse patent, PatchFinalDecisionRequest request, OffsetDateTime decidedAt
    ) {
        LegalActionResult legalActionResult = request.legalActionResult() != null
                ? request.legalActionResult() : patent.legalActionResult();
        LocalDate newDueDate = request.legalActionResult() == LegalActionResult.MAINTAINED
                && patent.legalActionResult() != LegalActionResult.MAINTAINED
                ? annualFeeScheduleService.advanceAfterMaintenance(
                        patent.country(), patent.feeDueDate(), patent.expectedExpirationDate())
                : patent.feeDueDate();
        String reason = request.reason() != null && !request.reason().isBlank()
                ? request.reason() : patent.finalDecisionRecord().reason();
        return new PatentDetailResponse(
                patent.patentId(), patent.managementNumber(), patent.applicationNumber(),
                patent.registrationNumber(), patent.title(), patent.draftTitle(),
                patent.businessArea(), patent.technologyArea(), patent.productName(),
                patent.country(), patent.coApplicants(), patent.applicationDate(),
                patent.registrationDate(), patent.expectedExpirationDate(),
                patent.departmentId(), patent.departmentName(),
                legalActionResult != null ? lifecycleStatusByLegalAction(legalActionResult) : patent.lifecycleStatus(),
                ReviewWorkflowStatus.NOT_IN_REVIEW, newDueDate, patent.reviewReason(),
                patent.currentRecommendation(), patent.businessOpinionDecision(), legalActionResult,
                patent.summary(), patent.aiEvaluationReport(),
                new FinalDecisionRecordResponse(
                        patent.finalDecisionRecord().decisionId() == null
                                ? patent.patentId() + "-DEC-01"
                                : patent.finalDecisionRecord().decisionId(),
                        reason, decidedAt),
                patent.businessOpinion(),
                false,
                false,
                patent.responseDueDate(),
                patent.responseDueDateExtendedUntil(),
                patent.urgentRequestedAt(),
                null);
    }

    PatentDetailResponse withReviewWorkflowStatus(PatentDetailResponse patent, ReviewWorkflowStatus status) {
        return new PatentDetailResponse(
                patent.patentId(), patent.managementNumber(), patent.applicationNumber(),
                patent.registrationNumber(), patent.title(), patent.draftTitle(),
                patent.businessArea(), patent.technologyArea(), patent.productName(),
                patent.country(), patent.coApplicants(), patent.applicationDate(),
                patent.registrationDate(), patent.expectedExpirationDate(),
                patent.departmentId(), patent.departmentName(), patent.lifecycleStatus(),
                status, patent.feeDueDate(), patent.reviewReason(),
                patent.currentRecommendation(), patent.businessOpinionDecision(),
                patent.legalActionResult(), patent.summary(), patent.aiEvaluationReport(),
                patent.finalDecisionRecord(), patent.businessOpinion(),
                status != ReviewWorkflowStatus.NOT_IN_REVIEW,
                patent.isDelayed(),
                patent.responseDueDate(),
                patent.responseDueDateExtendedUntil(),
                patent.urgentRequestedAt(),
                patent.currentQuarterKey());
    }

    // ── AI 레포트 응답 매핑 ───────────────────────────────────

    AiEvaluationReportResponse mapAgentResponse(AgentEvaluateResponse agent, String patentId) {
        if (agent == null) {
            throw new PatentFlowException(ErrorCode.AI_REPORT_FAILED);
        }
        String reportId = "REPORT-" + patentId + "-" + System.currentTimeMillis();
        List<EvaluationScoreResponse> scores = agent.scores() == null ? List.of() :
                agent.scores().stream()
                        .map(s -> new EvaluationScoreResponse(toCategory(s.category()), s.score(), s.evidence()))
                        .toList();
        // agent가 직접 계산한 totalScore를 우선 사용하고, 없으면 scores 합계로 보완
        Integer totalScore = agent.totalScore() != null
                ? agent.totalScore()
                : scores.stream().filter(s -> s.score() != null).mapToInt(EvaluationScoreResponse::score).sum();
        String summary = agent.summaryText();
        String rawMarkdown = normalizeMarkdown(agent.reportMarkdown(), summary, scores, agent.recommendation());
        String markdownFilePath = aiReportStorageService.storeMarkdown(patentId, reportId, rawMarkdown);
        return new AiEvaluationReportResponse(reportId, agent.generatedAt(),
                toRecommendation(agent.recommendation()), summary,
                totalScore == 0 ? null : totalScore, scores, List.of(), rawMarkdown, markdownFilePath);
    }

    private String normalizeMarkdown(
            String rawMarkdown, String summary,
            List<EvaluationScoreResponse> scores, String recommendation
    ) {
        if (rawMarkdown != null && !rawMarkdown.isBlank()) return rawMarkdown;

        StringBuilder md = new StringBuilder("# AI 특허 평가 레포트\n\n## 요약\n\n");
        md.append(summary == null || summary.isBlank() ? "작성 필요" : summary).append("\n\n## 평가 점수\n\n");
        if (scores == null || scores.isEmpty()) {
            md.append("- 평가 점수 없음\n");
        } else {
            for (EvaluationScoreResponse score : scores) {
                md.append("- ").append(score.category()).append(": ")
                  .append(score.score() == null ? "N/A" : score.score())
                  .append(" - ").append(score.evidence()).append("\n");
            }
        }
        md.append("\n## 권고\n\n").append(recommendation == null || recommendation.isBlank() ? "HOLD" : recommendation);
        return md.toString();
    }

    private Recommendation toRecommendation(String value) {
        if (value == null) return Recommendation.HOLD;
        String normalized = value.trim().toUpperCase();
        if (value.contains("유지")) return Recommendation.MAINTAIN;
        if (value.contains("포기")) return Recommendation.ABANDON;
        if (value.contains("추가") || value.contains("재검토") || value.contains("정보")) return Recommendation.REVIEW_AGAIN;
        return switch (normalized) {
            case "MAINTAIN" -> Recommendation.MAINTAIN;
            case "ABANDON" -> Recommendation.ABANDON;
            case "HOLD" -> Recommendation.HOLD;
            default -> Recommendation.REVIEW_AGAIN;
        };
    }

    private EvaluationCategory toCategory(String category) {
        if (category == null) return EvaluationCategory.BUSINESS_ALIGNMENT;
        return switch (category) {
            case "권리성" -> EvaluationCategory.RIGHTS;
            case "기술성" -> EvaluationCategory.TECHNOLOGY;
            case "시장성" -> EvaluationCategory.MARKET;
            case "사업 연계성", "사업연계성" -> EvaluationCategory.BUSINESS_ALIGNMENT;
            default -> EvaluationCategory.BUSINESS_ALIGNMENT;
        };
    }

    // ── 유틸 ─────────────────────────────────────────────────

    private boolean canRecordFinalDecision(ReviewWorkflowStatus status) {
        return status == ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED
                || status == ReviewWorkflowStatus.NOT_IN_REVIEW;
    }

    private PatentLifecycleStatus lifecycleStatusByLegalAction(LegalActionResult legalActionResult) {
        return switch (legalActionResult) {
            case MAINTAINED -> PatentLifecycleStatus.ACTIVE;
            case ABANDONED -> PatentLifecycleStatus.ABANDONED;
        };
    }
}
