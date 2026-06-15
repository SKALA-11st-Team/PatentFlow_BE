package com.syuuk.patentflow.patent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.patent.client.AiReportAgentClient;
import com.syuuk.patentflow.patent.client.AiReportAgentClient.AgentEvaluateResponse;
import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import com.syuuk.patentflow.patent.domain.PatentReviewHistoryEntity;
import com.syuuk.patentflow.patent.dto.AiEvaluationReportResponse;
import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import com.syuuk.patentflow.patent.dto.BusinessOpinionResponse;
import com.syuuk.patentflow.patent.dto.CoApplicantConsentRequest;
import com.syuuk.patentflow.patent.dto.CoApplicantConsentResponse;
import com.syuuk.patentflow.patent.dto.CoApplicantConsentStatus;
import com.syuuk.patentflow.patent.dto.EvaluationCategory;
import com.syuuk.patentflow.patent.dto.EvaluationScoreResponse;
import com.syuuk.patentflow.patent.dto.EvidenceDetailResponse;
import com.syuuk.patentflow.patent.dto.SourceResponse;
import com.syuuk.patentflow.patent.dto.FinalDecisionRecordResponse;
import com.syuuk.patentflow.patent.dto.FinalDecisionRequest;
import com.syuuk.patentflow.patent.dto.FinalDecisionResponse;
import com.syuuk.patentflow.patent.dto.LegalActionResult;
import com.syuuk.patentflow.patent.dto.PatchFinalDecisionRequest;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import com.syuuk.patentflow.patent.dto.PatentLifecycleStatus;
import com.syuuk.patentflow.patent.dto.PatentListItemResponse;
import com.syuuk.patentflow.patent.dto.PatentSummaryResponse;
import com.syuuk.patentflow.patent.dto.Recommendation;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 특허 검토 워크플로우 상태 전이 서비스.
 *
 * - AI 레포트 생성, 메일 발송 처리, 사업부 의견 접수, 최종 결정 등 상태 변경 작업을 담당한다.
 * - PatentReviewService와 순환 의존성이 생기므로 @Lazy로 주입받는다.
 * - with*() 메서드는 PatentDetailResponse를 순수하게 변환(immutable transform)하는 빌더 역할이다.
 */
@Service
public class PatentWorkflowService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * FR-LEGAL-06/18: AI 레포트 (재)생성이 허용되는 검토 워크플로우 상태.
     * 최초 생성(REVIEW_QUARTER_STARTED)뿐 아니라 레포트가 이미 존재하는 진행 상태(MAIL_READY,
     * WAITING_BUSINESS_RESPONSE, BUSINESS_RESPONSE_RECEIVED)에서 법무팀·사업부가 재생성할 수 있다.
     * 최종 처리 완료(LEGAL_ACTION_RECORDED)와 검토 분기 아님(NOT_IN_REVIEW)은 제외해 완료 기록을 보호한다.
     */
    public static final java.util.Set<ReviewWorkflowStatus> AI_REPORT_GENERATABLE_STATUSES = java.util.EnumSet.of(
            ReviewWorkflowStatus.REVIEW_QUARTER_STARTED,
            ReviewWorkflowStatus.MAIL_READY,
            ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE,
            ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED);

    // @Lazy: PatentReviewService ↔ PatentWorkflowService 순환 의존성을 런타임 프록시로 해소
    private final PatentReviewService patentReviewService;
    private final PatentMetadataRepository patentMetadataRepository;
    private final PatentReviewHistoryRepository reviewHistoryRepository;
    private final AiReportAgentClient aiReportAgentClient;
    private final AnnualFeeScheduleService annualFeeScheduleService;
    private final AiReportEditService aiReportEditService;
    private final com.syuuk.patentflow.settings.service.ValuationCriteriaService valuationCriteriaService;
    private final ObjectMapper objectMapper;

    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public PatentWorkflowService(
            @Lazy PatentReviewService patentReviewService,
            PatentMetadataRepository patentMetadataRepository,
            PatentReviewHistoryRepository reviewHistoryRepository,
            AiReportAgentClient aiReportAgentClient,
            AnnualFeeScheduleService annualFeeScheduleService,
            AiReportEditService aiReportEditService,
            com.syuuk.patentflow.settings.service.ValuationCriteriaService valuationCriteriaService,
            ObjectMapper objectMapper,
            org.springframework.context.ApplicationEventPublisher eventPublisher
    ) {
        this.patentReviewService = patentReviewService;
        this.patentMetadataRepository = patentMetadataRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.aiReportAgentClient = aiReportAgentClient;
        this.annualFeeScheduleService = annualFeeScheduleService;
        this.aiReportEditService = aiReportEditService;
        this.valuationCriteriaService = valuationCriteriaService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    // ── AI 레포트 생성 ────────────────────────────────────────

    // 온디맨드/배치 모두 비동기 잡(evaluateForBatch, 20분 타임아웃)으로 생성한다.
    // 인터랙티브 30초 동기 경로는 LLM 장시간 실행과 맞지 않아 제거했다(항상 타임아웃 강등 위험).
    public PatentDetailResponse generateAiReportForBatch(String patentId) {
        PatentDetailResponse patent = patentReviewService.findPatent(patentId);
        if (!AI_REPORT_GENERATABLE_STATUSES.contains(patent.reviewWorkflowStatus())) {
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS,
                    "AI 레포트는 검토 진행 중(최종 처리 완료 전) 상태에서만 생성/재생성할 수 있습니다.");
        }
        AgentEvaluateResponse agentResponse =
                aiReportAgentClient.evaluateForBatch(patentId, valuationCriteriaService.currentConfigForAgent(), patent);
        AiEvaluationReportResponse report = mapAgentResponse(agentResponse, patentId);
        aiReportEditService.logRegeneratedOverEdit(patentId, "SYSTEM");
        return patentReviewService.updatePatentInternal(patentId, p -> withAiReport(p, report, agentResponse.summaryText()));
    }

    // ── 메일 발송 처리 ────────────────────────────────────────

    @Transactional
    public PatentReviewService.WorkflowBatchUpdateResult markMailingSent(List<String> patentIds) {
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
            updatedPatentIds.add(updated.patentId());
        }

        return new PatentReviewService.WorkflowBatchUpdateResult(updatedPatentIds, skippedPatentIds);
    }

    // ── 사업부 의견 / 최종 결정 ───────────────────────────────

    @Transactional
    public void recordBusinessOpinion(
            String patentId,
            BusinessOpinionDecision decision,
            String reason,
            OffsetDateTime submittedAt
    ) {
        patentReviewService.updatePatentInternal(patentId, patent -> {
            if (patent.reviewWorkflowStatus() != ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE) {
                throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS,
                        "사업부 의견은 사업부 회신 대기 상태에서만 제출할 수 있습니다.");
            }
            return withBusinessOpinion(patent, decision, reason, submittedAt);
        });
    }

    @Transactional
    public FinalDecisionResponse patchFinalDecision(String patentId, PatchFinalDecisionRequest request, String actor) {
        OffsetDateTime decidedAt = OffsetDateTime.now(KST);
        PatentDetailResponse updated = patentReviewService.updatePatentInternal(patentId, patent -> {
            // FR-LEGAL-20: PATCH는 '이미 기록된 최종 판단'(LEGAL_ACTION_RECORDED)의 수정/취소 전용이다.
            // 형제 메서드(recordFinalDecision 등)와 동일하게 진입부에서 워크플로우 상태를 가드해
            // 최종 판단이 존재한 적 없는 단계에서 결정 레코드가 새로 만들어지거나 상태가 점프하는 것을 막는다.
            if (patent.reviewWorkflowStatus() != ReviewWorkflowStatus.LEGAL_ACTION_RECORDED) {
                throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS,
                        "최종 판단 수정/취소는 법무 처리 결과가 기록된 상태에서만 가능합니다.");
            }
            if (request.legalActionResult() == null && request.reason() == null) {
                return withClearedFinalDecision(patent);
            }
            return withPatchedFinalDecision(patent, request, decidedAt, actor);
        });
        return new FinalDecisionResponse(updated.patentId(), updated.finalDecisionRecord(),
                updated.legalActionResult(), updated.reviewWorkflowStatus());
    }

    @Transactional
    public FinalDecisionResponse recordFinalDecision(String patentId, FinalDecisionRequest request, String actor) {
        OffsetDateTime decidedAt = OffsetDateTime.now(KST);
        PatentDetailResponse updated = patentReviewService.updatePatentInternal(patentId, patent -> {
            if (patent.reviewWorkflowStatus() != ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED) {
                throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS);
            }
            // 공동출원 특허는 연차료 유지/포기 결정 전 공동출원인 합의(AGREED)가 필요하다.
            if (patent.jointApplication() && !isCoApplicantAgreed(patent)) {
                throw new PatentFlowException(ErrorCode.CO_APPLICANT_CONSENT_REQUIRED);
            }
            return withFinalDecision(patent, request, decidedAt, actor);
        });
        // NOTI-04: 최종 판단 기록 알림(커밋 후 발행).
        eventPublisher.publishEvent(new com.syuuk.patentflow.notification.event.WorkflowNotificationEvent(
                "최종 판단 기록",
                "%s 특허의 최종 판단(%s)이 기록되었습니다.".formatted(
                        patentId, updated.legalActionResult() == null ? "결정" : updated.legalActionResult().name()),
                "ADMIN",
                "/admin/patents/" + patentId));
        return new FinalDecisionResponse(updated.patentId(), updated.finalDecisionRecord(),
                updated.legalActionResult(), updated.reviewWorkflowStatus());
    }

    /**
     * 공동출원 특허의 공동출원인 합의를 기록한다(게이트 모델 — 워크플로 상태는 바꾸지 않음).
     * 공동출원이 아니거나 최종 판단 대기(BUSINESS_RESPONSE_RECEIVED) 상태가 아니면 거부한다.
     */
    @Transactional
    public PatentDetailResponse recordCoApplicantConsent(String patentId, CoApplicantConsentRequest request, String actor) {
        OffsetDateTime decidedAt = OffsetDateTime.now(KST);
        PatentDetailResponse updated = patentReviewService.updatePatentInternal(patentId, patent -> {
            if (!patent.jointApplication()) {
                throw new PatentFlowException(ErrorCode.NOT_A_JOINT_APPLICATION);
            }
            if (patent.reviewWorkflowStatus() != ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED) {
                throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS,
                        "공동출원인 합의는 최종 판단 대기(사업부 의견 접수) 상태에서만 기록할 수 있습니다.");
            }
            return withCoApplicantConsent(patent, request, decidedAt, actor);
        });
        eventPublisher.publishEvent(new com.syuuk.patentflow.notification.event.WorkflowNotificationEvent(
                "공동출원인 합의 기록",
                "%s 특허의 공동출원인 합의(%s)가 기록되었습니다.".formatted(patentId, request.status().name()),
                "ADMIN",
                "/admin/patents/" + patentId));
        return updated;
    }

    // ── 분기 / 배치 관리 ─────────────────────────────────────

    @Transactional
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
            if (history.getAiRecommendation() == null) {
                // 아직 AI 평가 전인 초기 상태. CONDITIONAL_MAINTAIN은 '조건부 유지' 전용이므로
                // 미평가 placeholder는 REVIEW_AGAIN('추가 정보 필요')으로 둔다.
                history.setAiRecommendation(Recommendation.REVIEW_AGAIN);
            }
            history.setAnnualFeeDueDate(dueDate);
            history.setDepartmentId(patentReviewService.departmentId(entity.getBusinessArea()));
            history.setDepartmentName(patentReviewService.departmentName(entity.getBusinessArea()));
            reviewHistoryRepository.save(history);
            reviewStarted.add(entity.getPatentId());
        });
        return reviewStarted;
    }

    // ── 상태 변환 빌더 (with*) ────────────────────────────────

    PatentDetailResponse withAiReport(PatentDetailResponse patent, AiEvaluationReportResponse report, String agentSummary) {
        return new PatentDetailResponse(
                patent.patentId(), patent.managementNumber(), patent.applicationNumber(),
                patent.registrationNumber(), patent.title(), patent.draftTitle(),
                patent.businessArea(), patent.technologyArea(), patent.productName(),
                patent.country(), patent.coApplicants(), patent.applicationDate(),
                patent.registrationDate(), patent.expectedExpirationDate(),
                patent.departmentId(), patent.departmentName(), patent.lifecycleStatus(),
                // 최초 생성(REVIEW_QUARTER_STARTED)만 MAIL_READY로 승급한다. 진행 상태에서의 재생성은
                // 워크플로우를 되돌리지 않도록 현재 상태를 보존한다(FR-LEGAL-06/18 재생성 지원).
                report.degraded() || patent.reviewWorkflowStatus() != ReviewWorkflowStatus.REVIEW_QUARTER_STARTED
                        ? patent.reviewWorkflowStatus()
                        : ReviewWorkflowStatus.MAIL_READY,
                patent.feeDueDate(), patent.reviewReason(),
                report.recommendation(), patent.businessOpinionDecision(), patent.legalActionResult(),
                withAgentSummary(patent.summary(), agentSummary), report,
                patent.finalDecisionRecord(), patent.businessOpinion(), true,
                patent.jointApplication(), patent.coApplicantConsent());
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
                patent.finalDecisionRecord(), new BusinessOpinionResponse(decision, reason, submittedAt), true,
                patent.jointApplication(), patent.coApplicantConsent());
    }

    private PatentDetailResponse withFinalDecision(
            PatentDetailResponse patent, FinalDecisionRequest request, OffsetDateTime decidedAt, String actor
    ) {
        LocalDate newDueDate = request.legalActionResult() == LegalActionResult.MAINTAINED
                ? annualFeeScheduleService.advanceAfterMaintenance(
                        patent.country(), patent.feeDueDate(), patent.expectedExpirationDate(),
                        nextMaintainRound(patent.patentId()))
                : patent.feeDueDate();
        return new PatentDetailResponse(
                patent.patentId(), patent.managementNumber(), patent.applicationNumber(),
                patent.registrationNumber(), patent.title(), patent.draftTitle(),
                patent.businessArea(), patent.technologyArea(), patent.productName(),
                patent.country(), patent.coApplicants(), patent.applicationDate(),
                patent.registrationDate(), patent.expectedExpirationDate(),
                patent.departmentId(), patent.departmentName(),
                lifecycleStatusByLegalAction(request.legalActionResult()),
                ReviewWorkflowStatus.LEGAL_ACTION_RECORDED, newDueDate, patent.reviewReason(),
                patent.currentRecommendation(), patent.businessOpinionDecision(),
                request.legalActionResult(), patent.summary(), patent.aiEvaluationReport(),
                new FinalDecisionRecordResponse(
                        patent.finalDecisionRecord().decisionId() == null
                                ? patent.patentId() + "-DEC-01"
                                : patent.finalDecisionRecord().decisionId(),
                        request.reason(), decidedAt, actor),
                patent.businessOpinion(), false,
                patent.jointApplication(), patent.coApplicantConsent());
    }

    /**
     * SETTINGS-11: 이번 유지 결정이 몇 회차인지 — 과거 분기 이력의 MAINTAINED 최종 결정 수 + 1.
     * 첫 유지가 1회차이며, 회차별 연장 기간 설정(country.extension.{CC}.rounds)의 인덱스가 된다.
     */
    private int nextMaintainRound(String patentId) {
        long maintainedCount = reviewHistoryRepository
                .countByPatentIdAndLegalActionResult(patentId, LegalActionResult.MAINTAINED);
        return (int) maintainedCount + 1;
    }

    private PatentDetailResponse withCoApplicantConsent(
            PatentDetailResponse patent, CoApplicantConsentRequest request, OffsetDateTime decidedAt, String actor
    ) {
        // 합의만 기록하고 상태·연차료·라이프사이클은 불변(게이트 모델).
        return new PatentDetailResponse(
                patent.patentId(), patent.managementNumber(), patent.applicationNumber(),
                patent.registrationNumber(), patent.title(), patent.draftTitle(),
                patent.businessArea(), patent.technologyArea(), patent.productName(),
                patent.country(), patent.coApplicants(), patent.applicationDate(),
                patent.registrationDate(), patent.expectedExpirationDate(),
                patent.departmentId(), patent.departmentName(), patent.lifecycleStatus(),
                patent.reviewWorkflowStatus(), patent.feeDueDate(), patent.reviewReason(),
                patent.currentRecommendation(), patent.businessOpinionDecision(), patent.legalActionResult(),
                patent.summary(), patent.aiEvaluationReport(), patent.finalDecisionRecord(),
                patent.businessOpinion(), patent.inReview(), patent.jointApplication(),
                new CoApplicantConsentResponse(request.status(), request.reason(), decidedAt, actor));
    }

    private PatentDetailResponse withClearedFinalDecision(PatentDetailResponse patent) {
        // 결정 레코드·법무 처리 결과를 비우고, 상태 복귀(LEGAL_ACTION_RECORDED→BUSINESS_RESPONSE_RECEIVED)는
        // 상수 직접 세팅 대신 중앙 전이표(withReviewWorkflowStatus)를 경유해 검증한다.
        PatentDetailResponse cleared = new PatentDetailResponse(
                patent.patentId(), patent.managementNumber(), patent.applicationNumber(),
                patent.registrationNumber(), patent.title(), patent.draftTitle(),
                patent.businessArea(), patent.technologyArea(), patent.productName(),
                patent.country(), patent.coApplicants(), patent.applicationDate(),
                patent.registrationDate(), patent.expectedExpirationDate(),
                patent.departmentId(), patent.departmentName(), patent.lifecycleStatus(),
                patent.reviewWorkflowStatus(), patent.feeDueDate(),
                patent.reviewReason(), patent.currentRecommendation(), patent.businessOpinionDecision(),
                null, patent.summary(), patent.aiEvaluationReport(),
                new FinalDecisionRecordResponse(null, null, null, null), patent.businessOpinion(),
                patent.inReview(),
                patent.jointApplication(), patent.coApplicantConsent());
        return withReviewWorkflowStatus(cleared, ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED);
    }

    private PatentDetailResponse withPatchedFinalDecision(
            PatentDetailResponse patent, PatchFinalDecisionRequest request, OffsetDateTime decidedAt, String actor
    ) {
        LegalActionResult legalActionResult = request.legalActionResult() != null
                ? request.legalActionResult() : patent.legalActionResult();
        LocalDate newDueDate = request.legalActionResult() == LegalActionResult.MAINTAINED
                && patent.legalActionResult() != LegalActionResult.MAINTAINED
                ? annualFeeScheduleService.advanceAfterMaintenance(
                        patent.country(), patent.feeDueDate(), patent.expectedExpirationDate(),
                        nextMaintainRound(patent.patentId()))
                : patent.feeDueDate();
        String reason = request.reason() != null && !request.reason().isBlank()
                ? request.reason() : patent.finalDecisionRecord().reason();
        // 상태는 LEGAL_ACTION_RECORDED 동일상태 멱등 수정이므로, 상수 직접 세팅 대신
        // 중앙 전이표(withReviewWorkflowStatus, this==target 허용)를 경유해 검증한다.
        PatentDetailResponse patched = new PatentDetailResponse(
                patent.patentId(), patent.managementNumber(), patent.applicationNumber(),
                patent.registrationNumber(), patent.title(), patent.draftTitle(),
                patent.businessArea(), patent.technologyArea(), patent.productName(),
                patent.country(), patent.coApplicants(), patent.applicationDate(),
                patent.registrationDate(), patent.expectedExpirationDate(),
                patent.departmentId(), patent.departmentName(),
                legalActionResult != null ? lifecycleStatusByLegalAction(legalActionResult) : patent.lifecycleStatus(),
                patent.reviewWorkflowStatus(), newDueDate, patent.reviewReason(),
                patent.currentRecommendation(), patent.businessOpinionDecision(), legalActionResult,
                patent.summary(), patent.aiEvaluationReport(),
                new FinalDecisionRecordResponse(
                        patent.finalDecisionRecord().decisionId() == null
                                ? patent.patentId() + "-DEC-01"
                                : patent.finalDecisionRecord().decisionId(),
                        reason, decidedAt, actor),
                patent.businessOpinion(), patent.inReview(),
                patent.jointApplication(), patent.coApplicantConsent());
        return withReviewWorkflowStatus(patched, ReviewWorkflowStatus.LEGAL_ACTION_RECORDED);
    }

    PatentDetailResponse withReviewWorkflowStatus(PatentDetailResponse patent, ReviewWorkflowStatus status) {
        // REVIEW-09: 무검증 상태 강제를 막는다 — 중앙 전이표(ReviewWorkflowStatus.canTransitionTo)로 검증.
        ReviewWorkflowStatus current = patent.reviewWorkflowStatus();
        if (current != null && !current.canTransitionTo(status)) {
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS,
                    "허용되지 않은 워크플로우 전이입니다: %s → %s".formatted(current, status));
        }
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
                status != ReviewWorkflowStatus.NOT_IN_REVIEW
                        && status != ReviewWorkflowStatus.LEGAL_ACTION_RECORDED,
                patent.jointApplication(), patent.coApplicantConsent());
    }

    // ── AI 레포트 응답 매핑 ───────────────────────────────────

    AiEvaluationReportResponse mapAgentResponse(AgentEvaluateResponse agent, String patentId) {
        String reportId = "REPORT-" + patentId + "-" + System.currentTimeMillis();
        List<EvaluationScoreResponse> scores = agent.scores() == null ? List.of() :
                agent.scores().stream()
                        .map(s -> new EvaluationScoreResponse(toCategory(s.category()), s.score(), s.grade(), s.evidence(),
                                toEvidenceDetails(s.evidenceDetails()),
                                nullSafeList(s.riskFactors()), nullSafeList(s.missingInformation()), s.confidence()))
                        .toList();
        Integer totalScore = totalScore(agent, scores);
        Double averageScore = averageScore(agent, totalScore, scores);
        boolean degraded = Boolean.TRUE.equals(agent.degraded())
                || hasFailureReason(agent)
                || scores.stream().noneMatch(s -> s.score() != null);
        String failureReason = failureReason(agent, degraded);
        String summary = agent.summaryText();
        String rawMarkdown = normalizeMarkdown(agent.reportMarkdown(), summary, scores, agent.recommendation());
        // AIREPORT-03/04: 마크다운 본문은 DB 컬럼(aiReportMarkdown)에 영속되고 파일은 읽히지 않는 중복이었다.
        // 파일 기록(무한 적재)·서버 절대경로 노출을 제거한다 — markdownFilePath는 더 이상 채우지 않는다(null).
        String markdownFilePath = null;
        OffsetDateTime generatedAt = agent.generatedAt() == null ? OffsetDateTime.now(KST) : agent.generatedAt();
        // ORCH-06/AIREPORT-02: 에이전트가 산출한 리치 근거를 폐기하지 않고 DTO로 풀스루한다.
        // UI-008: 적용된 가치평가 기준 스냅샷(appliedValuationConfig)도 함께 보존한다.
        return AiReportOverridesSupport.withAppliedCriteria(
                new AiEvaluationReportResponse(reportId, generatedAt,
                        toRecommendation(agent.recommendation()), summary,
                        totalScore, averageScore, agent.finalGrade(), degraded, failureReason,
                        scores, nullSafeList(agent.missingInformation()), rawMarkdown, markdownFilePath,
                        agent.keyEvidence(), nullSafeList(agent.judgementGrounds()),
                        nullSafeList(agent.businessCheckRequests()), toSourceResponses(agent.externalSources())),
                agent.appliedValuationConfig(), nullSafeList(agent.warnings()), agent.evidenceConfidence(),
                agent.summaryBrief(), agent.reportSections());
    }

    private static <T> List<T> nullSafeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private static List<EvidenceDetailResponse> toEvidenceDetails(List<AiReportAgentClient.EvidenceDetailItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> new EvidenceDetailResponse(item.text(), toSourceResponse(item.source())))
                .toList();
    }

    private static SourceResponse toSourceResponse(AiReportAgentClient.AgentSourceRef source) {
        return source == null ? null : new SourceResponse(source.title(), source.url());
    }

    private static List<SourceResponse> toSourceResponses(List<AiReportAgentClient.AgentSourceRef> sources) {
        if (sources == null) {
            return List.of();
        }
        return sources.stream().map(PatentWorkflowService::toSourceResponse).toList();
    }

    // 종합 점수(total/average)는 agent와 동일하게 핵심 3축(권리성·기술성·시장성)만 합산한다.
    // 사업 연계성(BUSINESS_ALIGNMENT)은 합산 대신 AI 권고 라벨 오버라이드로만 작용하므로 제외한다.
    private static final java.util.Set<EvaluationCategory> CORE_VALUATION_CATEGORIES = java.util.EnumSet.of(
            EvaluationCategory.RIGHTS, EvaluationCategory.TECHNOLOGY, EvaluationCategory.MARKET);

    private Integer totalScore(AgentEvaluateResponse agent, List<EvaluationScoreResponse> scores) {
        if (agent.totalScore() != null) {
            return agent.totalScore();
        }
        List<Integer> scoreValues = scores.stream()
                .filter(s -> CORE_VALUATION_CATEGORIES.contains(s.category()))
                .map(EvaluationScoreResponse::score)
                .filter(score -> score != null)
                .toList();
        if (scoreValues.isEmpty()) {
            return null;
        }
        return scoreValues.stream().mapToInt(Integer::intValue).sum();
    }

    private Double averageScore(AgentEvaluateResponse agent, Integer totalScore, List<EvaluationScoreResponse> scores) {
        if (agent.averageScore() != null) {
            return agent.averageScore();
        }
        long scoreCount = scores.stream()
                .filter(s -> CORE_VALUATION_CATEGORIES.contains(s.category()))
                .filter(score -> score.score() != null)
                .count();
        if (scoreCount > 0) {
            double sum = scores.stream()
                    .filter(s -> CORE_VALUATION_CATEGORIES.contains(s.category()))
                    .map(EvaluationScoreResponse::score)
                    .filter(score -> score != null)
                    .mapToInt(Integer::intValue)
                    .sum();
            return Math.round((sum / scoreCount) * 10.0) / 10.0;
        }
        if (totalScore != null) {
            // 점수 상세가 없는 폴백 경로 — 핵심 평가축(권리성·기술성·시장성 3축) 개수로 나눈 평균.
            return Math.round((totalScore / 3.0) * 10.0) / 10.0;
        }
        return null;
    }

    private String failureReason(AgentEvaluateResponse agent, boolean degraded) {
        if (agent.failureReason() != null && !agent.failureReason().isBlank()) {
            return agent.failureReason();
        }
        if (degraded) {
            return "AI 평가 점수 또는 근거가 충분히 생성되지 않았습니다.";
        }
        return null;
    }

    private boolean hasFailureReason(AgentEvaluateResponse agent) {
        return agent.failureReason() != null && !agent.failureReason().isBlank();
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
                  .append(score.grade() == null ? "" : " (" + score.grade() + ")")
                  .append(" - ").append(score.evidence()).append("\n");
            }
        }
        md.append("\n## 권고\n\n").append(recommendation == null || recommendation.isBlank() ? "추가 정보 필요" : recommendation);
        return md.toString();
    }

    private Recommendation toRecommendation(String value) {
        if (value == null) return Recommendation.REVIEW_AGAIN;
        String normalized = value.trim().toUpperCase();
        // CONDITIONAL_MAINTAIN enum은 AI 권고 '조건부 유지' 전용이다. '조건부 유지'는 '유지'를
        // 포함하므로 '유지'(MAINTAIN) 판정보다 먼저 처리한다.
        if (value.contains("조건부")) return Recommendation.CONDITIONAL_MAINTAIN;
        if (value.contains("유지")) return Recommendation.MAINTAIN;
        if (value.contains("포기")) return Recommendation.ABANDON;
        if (value.contains("추가") || value.contains("재검토") || value.contains("정보")) return Recommendation.REVIEW_AGAIN;
        return switch (normalized) {
            case "MAINTAIN" -> Recommendation.MAINTAIN;
            case "ABANDON" -> Recommendation.ABANDON;
            case "CONDITIONAL_MAINTAIN" -> Recommendation.CONDITIONAL_MAINTAIN;
            // 레거시 식별자 "HOLD"는 CONDITIONAL_MAINTAIN('조건부 유지')으로 리네임되었다.
            // 과거 데이터/외부 호출이 "HOLD"를 보내면 리네임 의미대로 CONDITIONAL_MAINTAIN으로 정규화한다.
            case "HOLD" -> Recommendation.CONDITIONAL_MAINTAIN;
            case "REVIEW_AGAIN" -> Recommendation.REVIEW_AGAIN;
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

    private boolean isCoApplicantAgreed(PatentDetailResponse patent) {
        return patent.coApplicantConsent() != null
                && patent.coApplicantConsent().status() == CoApplicantConsentStatus.AGREED;
    }

    private PatentLifecycleStatus lifecycleStatusByLegalAction(LegalActionResult legalActionResult) {
        return switch (legalActionResult) {
            case MAINTAINED -> PatentLifecycleStatus.ACTIVE;
            case ABANDONED -> PatentLifecycleStatus.ABANDONED;
        };
    }
}
