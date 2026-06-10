package com.syuuk.patentflow.settings.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/** 가치평가 기준 버전 이력 항목(계약 C3 — GET /api/v1/settings/valuation-criteria/history). */
public record ValuationCriteriaVersionResponse(
        int version,
        String createdBy,
        OffsetDateTime createdAt,
        Map<String, Object> config
) {
}
