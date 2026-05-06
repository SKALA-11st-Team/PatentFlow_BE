package com.syuuk.patentflow.business.dto;

import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BusinessChecklistSubmissionRequest(
        @NotBlank String patentId,
        String evaluatorName,
        String evaluatedAt,
        @NotEmpty List<@Valid BusinessChecklistResponseDto> responses,
        @Min(-5) @Max(5) int qualitativeScore,
        String qualitativeMemo,
        @NotNull BusinessOpinionDecision finalOpinion,
        String finalReason,
        String additionalNeeds
) {
}
