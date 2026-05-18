package com.syuuk.patentflow.settings.dto;

import java.util.List;

public record QuarterActivateResponse(
        String quarterKey,
        int reviewStartedCount,
        int autoCompletedCount,
        List<String> reviewStartedPatentIds,
        List<String> autoCompletedPatentIds
) {
}
