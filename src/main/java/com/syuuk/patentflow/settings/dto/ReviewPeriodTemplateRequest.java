package com.syuuk.patentflow.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReviewPeriodTemplateRequest(
        @NotNull @Min(1) @Max(12) Integer startMonth,
        @NotNull @Min(1) @Max(31) Integer startDay,
        @NotNull @Min(1) @Max(12) Integer endMonth,
        @NotNull @Min(1) @Max(31) Integer endDay
) {}
