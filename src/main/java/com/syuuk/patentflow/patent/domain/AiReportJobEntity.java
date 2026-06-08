package com.syuuk.patentflow.patent.domain;

import com.syuuk.patentflow.common.domain.BaseEntity;
import com.syuuk.patentflow.patent.dto.AiReportJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "patent_ai_report_jobs",
        indexes = {
                @Index(name = "idx_ai_report_job_patent_requested", columnList = "patent_id, requested_at"),
                @Index(name = "idx_ai_report_job_status", columnList = "status")
        })
public class AiReportJobEntity extends BaseEntity {

    @Id
    @Column(length = 128)
    private String jobId;

    @Column(name = "patent_id", nullable = false, length = 32)
    private String patentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiReportJobStatus status;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(length = 2000)
    private String message;

    @Column(name = "report_id", length = 128)
    private String reportId;

    protected AiReportJobEntity() {
    }

    public AiReportJobEntity(String jobId, String patentId, OffsetDateTime requestedAt) {
        this.jobId = jobId;
        this.patentId = patentId;
        this.status = AiReportJobStatus.PENDING;
        this.requestedAt = requestedAt;
    }

    public String getJobId() {
        return jobId;
    }

    public String getPatentId() {
        return patentId;
    }

    public AiReportJobStatus getStatus() {
        return status;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public String getMessage() {
        return message;
    }

    public String getReportId() {
        return reportId;
    }

    public void markRunning(OffsetDateTime startedAt) {
        this.status = AiReportJobStatus.RUNNING;
        this.startedAt = startedAt;
        this.message = "AI 평가 레포트 생성 중입니다.";
    }

    public void markSucceeded(OffsetDateTime finishedAt, String reportId) {
        this.status = AiReportJobStatus.SUCCEEDED;
        this.finishedAt = finishedAt;
        this.reportId = reportId;
        this.message = "AI 평가 레포트가 생성되었습니다.";
    }

    public void markDegraded(OffsetDateTime finishedAt, String reportId, String message) {
        this.status = AiReportJobStatus.DEGRADED;
        this.finishedAt = finishedAt;
        this.reportId = reportId;
        this.message = message;
    }

    public void markFailed(OffsetDateTime finishedAt, String message) {
        this.status = AiReportJobStatus.FAILED;
        this.finishedAt = finishedAt;
        this.message = message;
    }
}
