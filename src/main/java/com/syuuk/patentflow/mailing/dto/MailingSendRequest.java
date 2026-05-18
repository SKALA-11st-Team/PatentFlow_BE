package com.syuuk.patentflow.mailing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record MailingSendRequest(
        @NotEmpty List<@Valid BusinessReviewMailSendDraft> drafts
) {
}
