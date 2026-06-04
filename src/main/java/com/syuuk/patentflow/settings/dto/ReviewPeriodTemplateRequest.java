package com.syuuk.patentflow.settings.dto;

public record ReviewPeriodTemplateRequest(
        int startMonth,
        int startDay,
        int endMonth,
        int endDay
) {}
