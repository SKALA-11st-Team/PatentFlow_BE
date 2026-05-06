package com.syuuk.patentflow.business.dto;

import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BusinessChecklistSubmissionRequest(
        @NotBlank String patentId,
        String evaluatorName,
        String evaluatedAt,
        @NotNull List<BusinessChecklistResponseDto> responses,
        int qualitativeScore,
        String qualitativeMemo,
        @NotNull BusinessOpinionDecision finalOpinion,
        String finalReason,
        String additionalNeeds
) {
}
