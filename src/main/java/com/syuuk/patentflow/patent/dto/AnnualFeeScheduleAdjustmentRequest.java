package com.syuuk.patentflow.patent.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record AnnualFeeScheduleAdjustmentRequest(
        @NotNull LocalDate adjustedDueDate,
        String reason,
        String adjustedBy
) {
}
