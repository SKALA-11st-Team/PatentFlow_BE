package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.patent.dto.AuditLogEntryResponse;
import com.syuuk.patentflow.patent.repository.AiReportEditLogRepository;
import com.syuuk.patentflow.patent.repository.AnnualFeeAdjustmentRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @relatedFR FR-LEGAL-09, FR-LEGAL-10, FR-LEGAL-24
 * F4: 통합 감사 로그 — AI 레포트 편집(ai_report_edit_logs), 연차료 조정(annual_fee_adjustments),
 * 최종 결정(patent_review_history)을 시간 역순으로 합쳐 조회한다.
 * 데모 규모(수백 건)에 맞춘 메모리 병합 — 대량화되면 통합 테이블/뷰로 이전한다.
 */
@Service
public class AuditLogService {

    private final AiReportEditLogRepository editLogRepository;
    private final AnnualFeeAdjustmentRepository adjustmentRepository;
    private final PatentReviewHistoryRepository reviewHistoryRepository;

    public AuditLogService(
            AiReportEditLogRepository editLogRepository,
            AnnualFeeAdjustmentRepository adjustmentRepository,
            PatentReviewHistoryRepository reviewHistoryRepository
    ) {
        this.editLogRepository = editLogRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntryResponse> getAuditLogs(String type, String patentId, int limit) {
        List<AuditLogEntryResponse> entries = new ArrayList<>();

        if (typeMatches(type, AuditLogEntryResponse.TYPE_AI_REPORT_EDIT)) {
            editLogRepository.findAll().forEach(log -> entries.add(new AuditLogEntryResponse(
                    log.getId(),
                    AuditLogEntryResponse.TYPE_AI_REPORT_EDIT,
                    log.getPatentId(),
                    log.getEditor(),
                    "AI 레포트 %s (%s)".formatted(
                            log.getAction() == com.syuuk.patentflow.patent.domain.AiReportEditLogEntity.Action.REVERT
                                    ? "편집 되돌리기" : "편집",
                            log.getQuarterKey() == null ? "-" : log.getQuarterKey()),
                    log.getEditedAt())));
        }
        if (typeMatches(type, AuditLogEntryResponse.TYPE_FEE_ADJUSTMENT)) {
            adjustmentRepository.findAll().forEach(adjustment -> entries.add(new AuditLogEntryResponse(
                    adjustment.getAdjustmentId(),
                    AuditLogEntryResponse.TYPE_FEE_ADJUSTMENT,
                    adjustment.getPatentId(),
                    adjustment.getAdjustedBy(),
                    "연차료 납부일 조정 %s → %s (%s)".formatted(
                            adjustment.getPreviousDueDate(), adjustment.getAdjustedDueDate(),
                            adjustment.getReason() == null ? "-" : adjustment.getReason()),
                    adjustment.getAdjustedAt())));
        }
        if (typeMatches(type, AuditLogEntryResponse.TYPE_FINAL_DECISION)) {
            reviewHistoryRepository.findAll().stream()
                    .filter(history -> history.getFinalDecisionId() != null)
                    .forEach(history -> entries.add(new AuditLogEntryResponse(
                            history.getFinalDecisionId(),
                            AuditLogEntryResponse.TYPE_FINAL_DECISION,
                            history.getPatentId(),
                            history.getFinalDecisionDecidedBy(),
                            "최종 결정 %s (%s)".formatted(
                                    history.getLegalActionResult() == null ? "-" : history.getLegalActionResult().name(),
                                    history.getFinalDecisionReason() == null ? "-" : history.getFinalDecisionReason()),
                            history.getFinalDecisionDecidedAt())));
        }

        return entries.stream()
                .filter(entry -> patentId == null || patentId.isBlank() || patentId.equals(entry.patentId()))
                .sorted(Comparator.comparing(
                        AuditLogEntryResponse::occurredAt,
                        Comparator.nullsLast(Comparator.<OffsetDateTime>reverseOrder())))
                .limit(Math.max(1, Math.min(limit, 500)))
                .toList();
    }

    private boolean typeMatches(String filter, String type) {
        return filter == null || filter.isBlank() || "ALL".equalsIgnoreCase(filter) || type.equalsIgnoreCase(filter);
    }
}
