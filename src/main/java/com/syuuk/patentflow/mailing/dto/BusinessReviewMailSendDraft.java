package com.syuuk.patentflow.mailing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BusinessReviewMailSendDraft(
        @NotBlank String body,
        @NotEmpty List<@Valid BusinessReviewMailPatentSummary> patents,
        @NotBlank String recipientEmail,
        @NotBlank String recipientName,
        @NotBlank String subject
) {
}
