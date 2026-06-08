package com.syuuk.patentflow.patent.dto;

import java.time.LocalDate;
import java.util.List;

public record AnnualFeeScheduleItemResponse(
        String patentId,
        String managementNumber,
        String title,
        String country,
        boolean domesticPatent,
        LocalDate applicationDate,
        LocalDate registrationDate,
        LocalDate expectedExpirationDate,
        LocalDate annualFeeBaseDate,
        LocalDate calculatedAnnualFeeDueDate,
        LocalDate storedAnnualFeeDueDate,
        LocalDate effectiveAnnualFeeDueDate,
        LocalDate nextAnnualFeeDueDate,
        LocalDate adjustedAnnualFeeDueDate,
        String latestAdjustmentReason,
        int countryExtensionMonths,
        List<AnnualFeeAdjustmentHistoryResponse> adjustmentHistory
) {
}
