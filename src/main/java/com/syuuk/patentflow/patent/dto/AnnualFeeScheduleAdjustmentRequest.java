package com.syuuk.patentflow.patent.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AnnualFeeScheduleAdjustmentRequest(
        @NotNull LocalDate adjustedDueDate,
        @Size(max = 1000) String reason,
        String adjustedBy
) {
}
