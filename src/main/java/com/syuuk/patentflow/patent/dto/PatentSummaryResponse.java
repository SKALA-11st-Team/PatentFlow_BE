package com.syuuk.patentflow.patent.dto;

import java.util.List;

public record PatentSummaryResponse(
        String summaryText,
        String problemSolved,
        List<String> coreTechnicalPoints,
        String claimsSummary,
        List<String> missingFields
) {
}
