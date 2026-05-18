package com.syuuk.patentflow.settings.dto;

import java.time.LocalDate;

public record QuarterSettingRequest(
        LocalDate startDate,
        LocalDate endDate,
        LocalDate submissionDeadline
) {
}
