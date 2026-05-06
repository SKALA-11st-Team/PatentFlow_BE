package com.syuuk.patentflow.business.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BusinessChecklistResponseDto(
        @NotBlank String itemId,
        @NotNull @Min(1) @Max(4) Integer score,
        int aiSuggestedScore,
        String memo
) {
}
