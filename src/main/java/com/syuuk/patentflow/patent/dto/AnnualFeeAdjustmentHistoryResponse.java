package com.syuuk.patentflow.patent.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AnnualFeeAdjustmentHistoryResponse(
        String adjustmentId,
        LocalDate previousDueDate,
        LocalDate adjustedDueDate,
        String reason,
        String adjustedBy,
        OffsetDateTime adjustedAt
) {
}
