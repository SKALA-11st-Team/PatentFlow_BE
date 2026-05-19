package com.syuuk.patentflow.mailing.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record MailingHistoryItemResponse(
        String body,
        List<String> ccEmails,
        String mailingId,
        int patentCount,
        List<BusinessReviewMailPatentSummary> patents,
        String recipientEmail,
        String recipientName,
        OffsetDateTime sentAt,
        String sentBy,
        String status,
        String subject
) {
}
