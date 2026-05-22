package com.syuuk.patentflow.settings.dto;

import java.time.LocalDate;

public record ReviewScheduleRequest(
        int year,
        int mailLeadMonths,
        LocalDate businessResponseDueDate
) {
}
