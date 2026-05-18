package com.syuuk.patentflow.patent.dto;

import java.util.List;

public record ExecutiveApprovalBulkDecisionResponse(
        ExecutiveApprovalDecision decision,
        int updatedCount,
        List<String> updatedPatentIds,
        List<String> skippedPatentIds
) {
}