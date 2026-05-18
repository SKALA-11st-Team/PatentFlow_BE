package com.syuuk.patentflow.business.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "business_submissions")
public class BusinessSubmissionEntity {

    @Id
    @Column(length = 64)
    private String submissionId;

    @Column(nullable = false, length = 32)
    private String patentId;

    @Column(name = "quarter_key", length = 12)
    private String quarterKey;

    private int version;

    @Column(length = 64)
    private String decision;

    @Column(length = 2000)
    private String reason;

    @Column(length = 128)
    private String submittedBy;

    private OffsetDateTime submittedAt;

    private OffsetDateTime aiReportCreatedAt;

    @Column(length = 64)
    private String aiRecommendation;

    private int aiTotalScore;

    private int checklistTotal;

    private int qualitativeScore;

    @Column(columnDefinition = "TEXT")
    private String checklistScoresJson;

    protected BusinessSubmissionEntity() {
    }

    public BusinessSubmissionEntity(
            String submissionId,
            String patentId,
            String quarterKey,
            int version,
            String decision,
            String reason,
            String submittedBy,
            OffsetDateTime submittedAt,
            OffsetDateTime aiReportCreatedAt,
            String aiRecommendation,
            int aiTotalScore,
            int checklistTotal,
            int qualitativeScore,
            String checklistScoresJson
    ) {
        this.submissionId = submissionId;
        this.patentId = patentId;
        this.quarterKey = quarterKey;
        this.version = version;
        this.decision = decision;
        this.reason = reason;
        this.submittedBy = submittedBy;
        this.submittedAt = submittedAt;
        this.aiReportCreatedAt = aiReportCreatedAt;
        this.aiRecommendation = aiRecommendation;
        this.aiTotalScore = aiTotalScore;
        this.checklistTotal = checklistTotal;
        this.qualitativeScore = qualitativeScore;
        this.checklistScoresJson = checklistScoresJson;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public String getPatentId() {
        return patentId;
    }

    public String getQuarterKey() {
        return quarterKey;
    }

    public int getVersion() {
        return version;
    }

    public String getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public OffsetDateTime getAiReportCreatedAt() {
        return aiReportCreatedAt;
    }

    public String getAiRecommendation() {
        return aiRecommendation;
    }

    public int getAiTotalScore() {
        return aiTotalScore;
    }

    public int getChecklistTotal() {
        return checklistTotal;
    }

    public int getQualitativeScore() {
        return qualitativeScore;
    }

    public String getChecklistScoresJson() {
        return checklistScoresJson;
    }
}
