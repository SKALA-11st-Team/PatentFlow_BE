package com.syuuk.patentflow.patent.dto;

import java.time.OffsetDateTime;

/**
 * @relatedFR FR-LEGAL-09, FR-LEGAL-10, FR-LEGAL-24
 * F4: 통합 감사 로그 한 줄 — AI 레포트 편집 / 연차료 조정 / 최종 결정을 한 화면에서 추적한다.
 */
public record AuditLogEntryResponse(
        String id,
        String type,
        String patentId,
        String actor,
        String summary,
        OffsetDateTime occurredAt
) {

    public static final String TYPE_AI_REPORT_EDIT = "AI_REPORT_EDIT";
    public static final String TYPE_FEE_ADJUSTMENT = "FEE_ADJUSTMENT";
    public static final String TYPE_FINAL_DECISION = "FINAL_DECISION";
}
