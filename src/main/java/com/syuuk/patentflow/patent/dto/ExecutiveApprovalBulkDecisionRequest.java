package com.syuuk.patentflow.patent.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ExecutiveApprovalBulkDecisionRequest(
        @NotEmpty List<String> patentIds,
        ExecutiveApprovalDecision decision
) {
}