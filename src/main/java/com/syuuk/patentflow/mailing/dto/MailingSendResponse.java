package com.syuuk.patentflow.mailing.dto;

import java.util.List;

public record MailingSendResponse(
        String mailingBatchId,
        int updatedCount,
        int sentCount,
        int failedCount,
        List<String> updatedPatentIds,
        List<String> skippedPatentIds,
        List<String> failedRecipientEmails
) {
}
