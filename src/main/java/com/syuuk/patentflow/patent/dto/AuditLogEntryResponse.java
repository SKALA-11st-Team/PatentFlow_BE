/**
 * @author 유건욱
 * @date 2026-06-12
 */
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
        // AUDIT-02: 특허 ID만으로는 어떤 특허인지 알 수 없어 관리번호·특허명을 함께 내려준다(삭제된 특허는 null).
        String managementNumber,
        String patentTitle,
        String actor,
        String summary,
        OffsetDateTime occurredAt
) {

    public AuditLogEntryResponse withPatentInfo(String managementNumber, String patentTitle) {
        return new AuditLogEntryResponse(id, type, patentId, managementNumber, patentTitle, actor, summary, occurredAt);
    }

    public static final String TYPE_AI_REPORT_EDIT = "AI_REPORT_EDIT";
    public static final String TYPE_FEE_ADJUSTMENT = "FEE_ADJUSTMENT";
    public static final String TYPE_FINAL_DECISION = "FINAL_DECISION";
}
