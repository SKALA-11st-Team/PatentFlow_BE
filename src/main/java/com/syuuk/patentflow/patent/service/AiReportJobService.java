package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiReportJobService {

    private static final Logger log = LoggerFactory.getLogger(AiReportJobService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<AiReportJobStatus> ACTIVE_STATUSES =
            List.of(AiReportJobStatus.PENDING, AiReportJobStatus.RUNNING);

    private final AiReportJobRepository jobRepository;
    private final PatentReviewService patentReviewService;
    private final PatentWorkflowService workflowService;
    private final Executor aiReportBatchExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final AiReportAgentClient agentClient;

    public AiReportJobService(
            AiReportJobRepository jobRepository,
            PatentReviewService patentReviewService,
            PatentWorkflowService workflowService,
            @Qualifier("aiReportBatchExecutor") Executor aiReportBatchExecutor,
            ApplicationEventPublisher eventPublisher,
            AiReportAgentClient agentClient
    ) {
        this.jobRepository = jobRepository;
        this.patentReviewService = patentReviewService;
        this.workflowService = workflowService;
        this.aiReportBatchExecutor = aiReportBatchExecutor;
        this.eventPublisher = eventPublisher;
        this.agentClient = agentClient;
    }

    public AiReportJobResponse requestAiReport(String patentId) {
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
        AiReportJobEntity job = jobRepository.save(new AiReportJobEntity(newJobId(patentId, now), patentId, now));
        aiReportBatchExecutor.execute(() -> runJob(job.getJobId()));
        return toResponse(job);
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
