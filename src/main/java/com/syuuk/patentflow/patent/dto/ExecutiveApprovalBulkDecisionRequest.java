package com.syuuk.patentflow.patent.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ExecutiveApprovalBulkDecisionRequest(
        @NotEmpty List<String> patentIds,
        @NotNull
        ExecutiveApprovalDecision decision
) {
}
