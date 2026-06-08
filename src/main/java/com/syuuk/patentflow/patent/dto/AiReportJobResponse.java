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
        String reportId
) {
}
