package com.syuuk.patentflow.patent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * AI 레포트 법무 편집의 append-only 감사 로그.
 *
 * PatentReviewHistoryEntity의 ai_edit_* 컬럼은 '마지막 편집 상태'만 보존하므로,
 * 누가/언제/무엇을 수정·되돌렸는지의 전체 이력은 이 로그가 책임진다.
 */
@Entity
@Table(name = "ai_report_edit_logs",
        indexes = {
                @Index(name = "idx_ai_report_edit_log_patent", columnList = "patent_id, edited_at")
        })
public class AiReportEditLogEntity {

    public enum Action {
        EDIT,
        REVERT,
        // 편집이 존재하는 상태에서 레포트가 재생성되어 편집 기준이 낡아진(stale) 사건의 기록.
        REGENERATED_OVER_EDIT
    }

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "patent_id", nullable = false, length = 32)
    private String patentId;

    @Column(name = "quarter_key", length = 16)
    private String quarterKey;

    @Column(name = "editor", length = 128)
    private String editor;

    @Column(name = "edited_at", nullable = false)
    private OffsetDateTime editedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private Action action;

    // 이 시점의 누적 오버라이드 전체(JSON). REVERT/REGENERATED_OVER_EDIT는 직전 오버라이드를 기록한다.
    @Column(name = "overrides_json", columnDefinition = "TEXT")
    private String overridesJson;

    @Column(name = "base_report_id", length = 128)
    private String baseReportId;

    protected AiReportEditLogEntity() {
    }

    public AiReportEditLogEntity(
            String patentId, String quarterKey, String editor,
            OffsetDateTime editedAt, Action action, String overridesJson, String baseReportId
    ) {
        this.id = UUID.randomUUID().toString();
        this.patentId = patentId;
        this.quarterKey = quarterKey;
        this.editor = editor;
        this.editedAt = editedAt;
        this.action = action;
        this.overridesJson = overridesJson;
        this.baseReportId = baseReportId;
    }

    public String getId() {
        return id;
    }

    public String getPatentId() {
        return patentId;
    }

    public String getQuarterKey() {
        return quarterKey;
    }

    public String getEditor() {
        return editor;
    }

    public OffsetDateTime getEditedAt() {
        return editedAt;
    }

    public Action getAction() {
        return action;
    }

    public String getOverridesJson() {
        return overridesJson;
    }

    public String getBaseReportId() {
        return baseReportId;
    }
}
