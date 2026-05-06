package com.syuuk.patentflow.patent.dto;

import java.time.OffsetDateTime;

public record BusinessOpinionResponse(
        BusinessOpinionDecision decision,
        String reason,
        OffsetDateTime submittedAt
) {
}
