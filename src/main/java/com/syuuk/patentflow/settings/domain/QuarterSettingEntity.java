package com.syuuk.patentflow.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "quarter_settings")
public class QuarterSettingEntity {

    @Id
    @Column(length = 12)
    private String quarterKey; // "2026-Q1"

    private int year;

    private int quarterNumber; // 1–4

    private LocalDate startDate;

    private LocalDate endDate;

    private boolean activated;

    private OffsetDateTime activatedAt;

    private boolean ended;

    private OffsetDateTime endedAt;

    private LocalDate submissionDeadline;

    protected QuarterSettingEntity() {
    }

    public QuarterSettingEntity(String quarterKey, int year, int quarterNumber,
            LocalDate startDate, LocalDate endDate) {
        this.quarterKey = quarterKey;
        this.year = year;
        this.quarterNumber = quarterNumber;
        this.startDate = startDate;
        this.endDate = endDate;
        this.activated = false;
        this.activatedAt = null;
        this.submissionDeadline = null;
    }

    public String getQuarterKey() { return quarterKey; }
    public int getYear() { return year; }
    public int getQuarterNumber() { return quarterNumber; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isActivated() { return activated; }
    public OffsetDateTime getActivatedAt() { return activatedAt; }
    public LocalDate getSubmissionDeadline() { return submissionDeadline; }
    public boolean isEnded() { return ended; }
    public OffsetDateTime getEndedAt() { return endedAt; }

    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public void setActivated(boolean activated) { this.activated = activated; }
    public void setActivatedAt(OffsetDateTime activatedAt) { this.activatedAt = activatedAt; }
    public void setSubmissionDeadline(LocalDate submissionDeadline) { this.submissionDeadline = submissionDeadline; }
    public void setEnded(boolean ended) { this.ended = ended; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
}
