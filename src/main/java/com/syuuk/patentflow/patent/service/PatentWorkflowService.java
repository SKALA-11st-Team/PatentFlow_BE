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

    // @Lazy: PatentReviewService ↔ PatentWorkflowService 순환 의존성을 런타임 프록시로 해소
    private final PatentReviewService patentReviewService;
    private final PatentMetadataRepository patentMetadataRepository;
    private final PatentReviewHistoryRepository reviewHistoryRepository;
    private final AiReportAgentClient aiReportAgentClient;
    private final AiReportStorageService aiReportStorageService;
    private final AnnualFeeScheduleService annualFeeScheduleService;
    private final ObjectMapper objectMapper;

    public PatentWorkflowService(
            @Lazy PatentReviewService patentReviewService,
            PatentMetadataRepository patentMetadataRepository,
            PatentReviewHistoryRepository reviewHistoryRepository,
            AiReportAgentClient aiReportAgentClient,
            AiReportStorageService aiReportStorageService,
            AnnualFeeScheduleService annualFeeScheduleService,
            ObjectMapper objectMapper
    ) {
        this.patentReviewService = patentReviewService;
        this.patentMetadataRepository = patentMetadataRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.aiReportAgentClient = aiReportAgentClient;
        this.aiReportStorageService = aiReportStorageService;
        this.annualFeeScheduleService = annualFeeScheduleService;
        this.objectMapper = objectMapper;
    }

    // ── AI 레포트 생성 ────────────────────────────────────────

    public PatentDetailResponse generateAiReport(String patentId) {
        PatentDetailResponse patent = patentReviewService.findPatent(patentId);
        if (patent.reviewWorkflowStatus() != ReviewWorkflowStatus.REVIEW_QUARTER_STARTED) {
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

    @Transactional
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

    // ── 분기 / 배치 관리 ─────────────────────────────────────

    @Transactional
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

    // ── 상태 변환 빌더 (with*) ────────────────────────────────

    PatentDetailResponse withAiReport(PatentDetailResponse patent, AiEvaluationReportResponse report, String agentSummary) {
        return new PatentDetailResponse(
                patent.patentId(), patent.managementNumber(), patent.applicationNumber(),
                patent.registrationNumber(), patent.title(), patent.draftTitle(),
                patent.businessArea(), patent.technologyArea(), patent.productName(),
                patent.country(), patent.coApplicants(), patent.applicationDate(),
                patent.registrationDate(), patent.expectedExpirationDate(),
                patent.departmentId(), patent.departmentName(), patent.lifecycleStatus(),
                report.degraded() ? patent.reviewWorkflowStatus() : ReviewWorkflowStatus.MAIL_READY,
                patent.feeDueDate(), patent.reviewReason(),
                report.recommendation(), patent.businessOpinionDecision(), patent.legalActionResult(),
                withAgentSummary(patent.summary(), agentSummary), report,
                patent.finalDecisionRecord(), patent.businessOpinion(), true);
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
                patent.finalDecisionRecord(), new BusinessOpinionResponse(decision, reason, submittedAt), true);
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
                patent.businessOpinion(), false);
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
                new FinalDecisionRecordResponse(null, null, null), patent.businessOpinion(), true);
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
                patent.businessOpinion(), false);
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
                status != ReviewWorkflowStatus.NOT_IN_REVIEW);
    }

    // ── AI 레포트 응답 매핑 ───────────────────────────────────

    AiEvaluationReportResponse mapAgentResponse(AgentEvaluateResponse agent, String patentId) {
        String reportId = "REPORT-" + patentId + "-" + System.currentTimeMillis();
        List<EvaluationScoreResponse> scores = agent.scores() == null ? List.of() :
                agent.scores().stream()
                        .map(s -> new EvaluationScoreResponse(toCategory(s.category()), s.score(), s.grade(), s.evidence(),
                                toEvidenceDetails(s.evidenceDetails())))
                        .toList();
        Integer totalScore = totalScore(agent, scores);
        Double averageScore = averageScore(agent, totalScore, scores);
        boolean degraded = Boolean.TRUE.equals(agent.degraded()) || scores.stream().noneMatch(s -> s.score() != null);
        String failureReason = failureReason(agent, degraded);
        String summary = agent.summaryText();
        String rawMarkdown = normalizeMarkdown(agent.reportMarkdown(), summary, scores, agent.recommendation());
        String markdownFilePath = aiReportStorageService.storeMarkdown(patentId, reportId, rawMarkdown);
        OffsetDateTime generatedAt = agent.generatedAt() == null ? OffsetDateTime.now(KST) : agent.generatedAt();
        // ORCH-06/AIREPORT-02: 에이전트가 산출한 리치 근거를 폐기하지 않고 DTO로 풀스루한다.
        return new AiEvaluationReportResponse(reportId, generatedAt,
                toRecommendation(agent.recommendation()), summary,
                totalScore, averageScore, agent.finalGrade(), agent.finalIndicator(), degraded, failureReason,
                scores, nullSafeList(agent.missingInformation()), rawMarkdown, markdownFilePath,
                agent.keyEvidence(), nullSafeList(agent.judgementGrounds()),
                nullSafeList(agent.businessCheckRequests()), toSourceResponses(agent.externalSources()));
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

    private Integer totalScore(AgentEvaluateResponse agent, List<EvaluationScoreResponse> scores) {
        if (agent.totalScore() != null) {
            return agent.totalScore();
        }
        List<Integer> scoreValues = scores.stream()
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
        long scoreCount = scores.stream().filter(score -> score.score() != null).count();
        if (scoreCount > 0) {
            double sum = scores.stream()
                    .map(EvaluationScoreResponse::score)
                    .filter(score -> score != null)
                    .mapToInt(Integer::intValue)
                    .sum();
            return Math.round((sum / scoreCount) * 10.0) / 10.0;
        }
        if (totalScore != null) {
            return Math.round((totalScore / 4.0) * 10.0) / 10.0;
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
        return status == ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED;
    }

    private PatentLifecycleStatus lifecycleStatusByLegalAction(LegalActionResult legalActionResult) {
        return switch (legalActionResult) {
            case MAINTAINED -> PatentLifecycleStatus.ACTIVE;
            case ABANDONED -> PatentLifecycleStatus.ABANDONED;
        };
    }
}
