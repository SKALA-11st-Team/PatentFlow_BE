package com.syuuk.patentflow.patent.dto;

import java.time.OffsetDateTime;

public record AiReportJobResponse(
        String jobId,
        String patentId,
        AiReportJobStatus status,
        OffsetDateTime requestedAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String message,
        String reportId,
        // W1: RUNNING 상태일 때 agent 진행 단계(근거 수집/압축/평가/작성). 미조회·미지원이면 null.
        String progressStage,
        String progressStageLabel
) {

    public AiReportJobResponse withProgress(String stage, String stageLabel) {
        return new AiReportJobResponse(jobId, patentId, status, requestedAt, startedAt, finishedAt,
                message, reportId, stage, stageLabel);
    }
}
