package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.patent.domain.AiReportJobEntity;
import com.syuuk.patentflow.patent.dto.AiEvaluationReportResponse;
import com.syuuk.patentflow.patent.dto.AiReportJobResponse;
import com.syuuk.patentflow.patent.dto.AiReportJobStatus;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.repository.AiReportJobRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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

    public AiReportJobService(
            AiReportJobRepository jobRepository,
            PatentReviewService patentReviewService,
            PatentWorkflowService workflowService,
            @Qualifier("aiReportBatchExecutor") Executor aiReportBatchExecutor
    ) {
        this.jobRepository = jobRepository;
        this.patentReviewService = patentReviewService;
        this.workflowService = workflowService;
        this.aiReportBatchExecutor = aiReportBatchExecutor;
    }

    public AiReportJobResponse requestAiReport(String patentId) {
        PatentDetailResponse patent = patentReviewService.findPatent(patentId);
        if (patent.reviewWorkflowStatus() != ReviewWorkflowStatus.REVIEW_QUARTER_STARTED) {
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS,
                    "AI 레포트는 검토 시작(REVIEW_QUARTER_STARTED) 상태에서만 생성할 수 있습니다.");
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
                .orElseGet(() -> new AiReportJobResponse(
                        null,
                        patentId,
                        null,
                        null,
                        null,
                        null,
                        "AI 평가 레포트 생성 요청 내역이 없습니다.",
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
                job.getReportId());
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
