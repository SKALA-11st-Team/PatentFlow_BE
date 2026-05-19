package com.syuuk.patentflow.mailing.dto;

import jakarta.validation.constraints.NotBlank;

public record BusinessReviewMailPatentSummary(
        @NotBlank String patentId,
        String managementNumber,
        String title
) {
}
