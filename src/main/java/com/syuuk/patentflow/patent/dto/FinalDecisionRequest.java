package com.syuuk.patentflow.patent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FinalDecisionRequest(
        @NotNull LegalActionResult legalActionResult,
        @NotBlank String reason
) {
}
