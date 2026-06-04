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

    @Column(name = "setting_year")
    private int year;

    private int quarterNumber;

    private LocalDate startDate;

    private LocalDate endDate;

    private boolean activated;

    private OffsetDateTime activatedAt;

    // 납부 기간(endDate)이 지난 분기를 진행 중인 분기와 구분하기 위해 복구.
    // 수동 종료 API 없이 QuarterActivationScheduler가 매일 자정에 자동 처리한다.
    private boolean ended;

    private OffsetDateTime endedAt;

    private LocalDate submissionDeadline;

    // 활성화 시점의 mailLeadMonths 스냅샷 — 이후 설정 변경 시에도 이력이 바뀌지 않도록 보존
    private Integer mailLeadMonthsSnapshot;

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
        this.ended = false;
    }

    public String getQuarterKey() { return quarterKey; }
    public int getYear() { return year; }
    public int getQuarterNumber() { return quarterNumber; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isActivated() { return activated; }
    public OffsetDateTime getActivatedAt() { return activatedAt; }
    public boolean isEnded() { return ended; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public LocalDate getSubmissionDeadline() { return submissionDeadline; }

    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public void setActivated(boolean activated) { this.activated = activated; }
    public void setActivatedAt(OffsetDateTime activatedAt) { this.activatedAt = activatedAt; }
    public void setEnded(boolean ended) { this.ended = ended; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
    public void setSubmissionDeadline(LocalDate submissionDeadline) { this.submissionDeadline = submissionDeadline; }
    public Integer getMailLeadMonthsSnapshot() { return mailLeadMonthsSnapshot; }
    public void setMailLeadMonthsSnapshot(Integer mailLeadMonthsSnapshot) { this.mailLeadMonthsSnapshot = mailLeadMonthsSnapshot; }
}
