package com.syuuk.patentflow.patent.dto;

import java.time.OffsetDateTime;

public record PatentHistoryResponse(
        String historyId,
        String type,
        String title,
        String description,
        String actorName,
        OffsetDateTime createdAt
) {
}
