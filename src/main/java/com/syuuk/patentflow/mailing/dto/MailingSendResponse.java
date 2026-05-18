package com.syuuk.patentflow.mailing.dto;

import java.util.List;

public record MailingSendResponse(
        String mailingBatchId,
        int updatedCount,
        List<String> updatedPatentIds,
        List<String> skippedPatentIds
) {
}
