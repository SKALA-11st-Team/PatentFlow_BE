package com.syuuk.patentflow.settings.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record QuarterSettingResponse(
        String quarterKey,
        int year,
        int quarterNumber,
        String quarterLabel,
        LocalDate startDate,
        LocalDate endDate,
        boolean activated,
        OffsetDateTime activatedAt,
        boolean ended,
        OffsetDateTime endedAt,
        int targetPatentCount,
        LocalDate submissionDeadline
) {
}
