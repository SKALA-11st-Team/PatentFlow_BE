package com.syuuk.patentflow.mailing.dto;

import java.util.List;

public record MailingSendResponse(
        int updatedCount,
        List<String> updatedPatentIds
) {
}
