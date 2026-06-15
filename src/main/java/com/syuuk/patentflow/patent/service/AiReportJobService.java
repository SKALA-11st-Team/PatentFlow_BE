package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.patent.domain.AiReportJobEntity;
import com.syuuk.patentflow.patent.dto.AiEvaluationReportResponse;
import com.syuuk.patentflow.patent.dto.AiReportJobResponse;
import com.syuuk.patentflow.patent.dto.AiReportJobStatus;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import com.syuuk.patentflow.patent.client.AiReportAgentClient;
import com.syuuk.patentflow.notification.event.WorkflowNotificationEvent;
import com.syuuk.patentflow.patent.repository.AiReportJobRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiReportJobService {

    private static final Logger log = LoggerFactory.getLogger(AiReportJobService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<AiReportJobStatus> ACTIVE_STATUSES =
            List.of(AiReportJobStatus.PENDING, AiReportJobStatus.RUNNING);
    private static final String ROLE_BUSINESS = "ROLE_BUSINESS";

    private final AiReportJobRepository jobRepository;
    private final PatentReviewService patentReviewService;
    private final PatentWorkflowService workflowService;
    private final Executor aiReportBatchExecutor;
    private final Executor aiReportOnDemandExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final AiReportAgentClient agentClient;
    private final SystemSettingsService systemSettingsService;

    public AiReportJobService(
            AiReportJobRepository jobRepository,
            PatentReviewService patentReviewService,
            PatentWorkflowService workflowService,
            @Qualifier("aiReportBatchExecutor") Executor aiReportBatchExecutor,
            @Qualifier("aiReportOnDemandExecutor") Executor aiReportOnDemandExecutor,
            ApplicationEventPublisher eventPublisher,
            AiReportAgentClient agentClient,
            SystemSettingsService systemSettingsService
    ) {
        this.jobRepository = jobRepository;
        this.patentReviewService = patentReviewService;
        this.workflowService = workflowService;
        this.aiReportBatchExecutor = aiReportBatchExecutor;
        this.aiReportOnDemandExecutor = aiReportOnDemandExecutor;
        this.eventPublisher = eventPublisher;
        this.agentClient = agentClient;
        this.systemSettingsService = systemSettingsService;
    }

    // NOTE: @Transactional을 붙이지 않는다 — 아래 잡 row가 커밋된 뒤에야 비동기 executor가
    // runJob()의 findById(jobId)로 안전하게 조회할 수 있기 때문이다(트랜잭션 미커밋 상태의 row를
    // 다른 스레드가 못 읽는 race 방지). 동시 중복은 saveAndFlush + DataIntegrityViolation 처리로 막는다.
    public AiReportJobResponse requestAiReport(String patentId) {
        // FR-LEGAL-18: 재생성 권한 설정은 서버에서도 강제한다. 기본값(false)에서 BUSINESS는 재평가를
        // 직접 트리거할 수 없다(장시간 LLM 호출·근거수집 비용 방지). 관리자가 true로 켜야만 허용.
        if (isBusinessActor() && !systemSettingsService.getAiReportRegenBusinessAllowed()) {
            throw new PatentFlowException(ErrorCode.ACCESS_DENIED,
                    "AI 레포트 재생성 권한이 없습니다. 관리자에게 문의해 주세요.");
        }

        PatentDetailResponse patent = patentReviewService.findPatent(patentId);
        // FR-LEGAL-06/18: 최초 생성뿐 아니라 진행 상태(레포트 존재)에서도 법무팀·사업부가 재생성할 수 있다.
        if (!PatentWorkflowService.AI_REPORT_GENERATABLE_STATUSES.contains(patent.reviewWorkflowStatus())) {
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS,
                    "AI 레포트는 검토 진행 중(최종 처리 완료 전) 상태에서만 생성/재생성할 수 있습니다.");
        }

        AiReportJobEntity existing = jobRepository
                .findFirstByPatentIdAndStatusInOrderByRequestedAtDesc(patentId, ACTIVE_STATUSES)
                .orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        OffsetDateTime now = now();
        AiReportJobEntity job;
        try {
            job = jobRepository.saveAndFlush(new AiReportJobEntity(newJobId(patentId, now), patentId, now));
        } catch (DataIntegrityViolationException duplicate) {
            // TOCTOU: 거의 동시에 같은 특허로 두 요청이 들어온 경우, 활성 상태 부분 유니크 제약이
            // INSERT를 한쪽만 통과시킨다. 충돌한 요청은 기존 활성 잡을 재조회해 그 응답을 반환한다
            // (중복 에이전트 평가·잡 생성 방지).
            log.info("[AiReportJob] concurrent request collapsed for patent {}: {}", patentId, duplicate.getMessage());
            return jobRepository
                    .findFirstByPatentIdAndStatusInOrderByRequestedAtDesc(patentId, ACTIVE_STATUSES)
                    .map(this::toResponse)
                    .orElseThrow(() -> duplicate);
        }
        // 온디맨드 잡은 배치 풀과 분리된 aiReportOnDemandExecutor에 제출한다(배치가 스레드를 장시간
        // 점유해도 굶지 않음). 풀+큐 포화로 거부되면 잡을 PENDING으로 남겨 orphan cleaner/재요청이
        // 처리하게 하고 HTTP 스레드를 블로킹하지 않는다.
        try {
            aiReportOnDemandExecutor.execute(() -> runJob(job.getJobId()));
        } catch (RejectedExecutionException rejected) {
            log.warn("[AiReportJob] on-demand executor saturated; leaving job {} PENDING: {}",
                    job.getJobId(), rejected.getMessage());
        }
        return toResponse(job);
    }

    private boolean isBusinessActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_BUSINESS.equals(authority.getAuthority()));
    }

    @Transactional(readOnly = true)
    public AiReportJobResponse latestStatus(String patentId) {
        patentReviewService.ensurePatentExists(patentId);
        return jobRepository.findFirstByPatentIdOrderByRequestedAtDesc(patentId)
                .map(this::toResponse)
                .map(this::withAgentProgress)
                .orElseGet(() -> new AiReportJobResponse(
                        null,
                        patentId,
                        null,
                        null,
                        null,
                        null,
                        "AI 평가 레포트 생성 요청 내역이 없습니다.",
                        null,
                        null,
                        null));
    }

    void runJob(String jobId) {
        AiReportJobEntity job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("[AiReportJob] job not found: {}", jobId);
            return;
        }

        try {
            job.markRunning(now());
            jobRepository.save(job);

            PatentDetailResponse updated = workflowService.generateAiReportForBatch(job.getPatentId());
            AiEvaluationReportResponse report = updated.aiEvaluationReport();
            if (report.degraded()) {
                job.markDegraded(now(), report.reportId(), degradedMessage(report));
            } else {
                job.markSucceeded(now(), report.reportId());
            }
            jobRepository.save(job);
            // NOTI-04: AI 레포트 생성 완료를 알림으로 발행(제한 생성=degraded도 결과물이 있으므로 발행).
            String title = report.degraded() ? "AI 평가 레포트 생성 완료(제한)" : "AI 평가 레포트 생성 완료";
            eventPublisher.publishEvent(new WorkflowNotificationEvent(
                    title,
                    "%s 특허의 AI 평가 레포트가 생성되었습니다.".formatted(job.getPatentId()),
                    "ADMIN",
                    "/admin/patents/" + job.getPatentId()));
        } catch (Exception exception) {
            log.warn("[AiReportJob] job failed: {} — {}", jobId, exception.getMessage());
            AiReportJobEntity failedJob = jobRepository.findById(jobId).orElse(job);
            failedJob.markFailed(now(), userMessage(exception));
            jobRepository.save(failedJob);
        }
    }

    private AiReportJobResponse toResponse(AiReportJobEntity job) {
        return new AiReportJobResponse(
                job.getJobId(),
                job.getPatentId(),
                job.getStatus(),
                job.getRequestedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getMessage(),
                job.getReportId(),
                null,
                null);
    }

    /** W1: RUNNING 잡은 agent 진행 단계를 함께 내려준다(조회 실패는 무해 — 진행 없이 반환). */
    private AiReportJobResponse withAgentProgress(AiReportJobResponse response) {
        if (response.status() != AiReportJobStatus.RUNNING) {
            return response;
        }
        AiReportAgentClient.AgentProgress progress = agentClient.fetchProgress(response.patentId());
        if (progress == null) {
            return response;
        }
        return response.withProgress(progress.stage(), progress.stageLabel());
    }

    private String newJobId(String patentId, OffsetDateTime now) {
        return "AIJOB-" + patentId + "-" + now.toInstant().toEpochMilli();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(KST);
    }

    private String degradedMessage(AiEvaluationReportResponse report) {
        if (report.failureReason() != null && !report.failureReason().isBlank()) {
            return report.failureReason();
        }
        return "AI 평가가 제한된 근거로 생성되었습니다.";
    }

    private String userMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "AI 평가 레포트 생성에 실패했습니다."
                : message;
    }
}
