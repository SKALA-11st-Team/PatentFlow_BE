package com.syuuk.patentflow.settings.dto;

public record ReviewPeriodTemplateResponse(
        int periodNumber,
        int startMonth,
        int startDay,
        int endMonth,
        int endDay,
        String label
) {}
