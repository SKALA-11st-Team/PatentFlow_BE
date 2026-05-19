package com.syuuk.patentflow.patent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "patent_review_history",
        uniqueConstraints = @UniqueConstraint(columnNames = {"patent_id", "quarter_key"}))
public class PatentReviewHistoryEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "patent_id", nullable = false, length = 32)
    private String patentId;

    @Column(name = "quarter_key", nullable = false, length = 16)
    private String quarterKey;

    @Column(length = 64)
    private String reviewWorkflowStatus;

    @Column(length = 64)
    private String businessOpinionDecision;

    @Column(length = 2000)
    private String businessOpinionReason;

    private OffsetDateTime businessOpinionSubmittedAt;

    @Column(length = 64)
    private String legalActionResult;

    @Column(length = 64)
    private String finalDecisionId;

    @Column(length = 2000)
    private String finalDecisionReason;

    private OffsetDateTime finalDecisionDecidedAt;

    @Column(name = "annual_fee_due_date")
    private LocalDate annualFeeDueDate;

    @Column(name = "department_id", length = 64)
    private String departmentId;

    @Column(name = "department_name", length = 128)
    private String departmentName;

    private OffsetDateTime createdAt;

    protected PatentReviewHistoryEntity() {
    }

    public PatentReviewHistoryEntity(String patentId, String quarterKey) {
        this.id = patentId + "|" + quarterKey;
        this.patentId = patentId;
        this.quarterKey = quarterKey;
        this.createdAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"));
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

    public String getReviewWorkflowStatus() {
        return reviewWorkflowStatus;
    }

    public void setReviewWorkflowStatus(String reviewWorkflowStatus) {
        this.reviewWorkflowStatus = reviewWorkflowStatus;
    }

    public String getBusinessOpinionDecision() {
        return businessOpinionDecision;
    }

    public void setBusinessOpinionDecision(String businessOpinionDecision) {
        this.businessOpinionDecision = businessOpinionDecision;
    }

    public String getBusinessOpinionReason() {
        return businessOpinionReason;
    }

    public void setBusinessOpinionReason(String businessOpinionReason) {
        this.businessOpinionReason = businessOpinionReason;
    }

    public OffsetDateTime getBusinessOpinionSubmittedAt() {
        return businessOpinionSubmittedAt;
    }

    public void setBusinessOpinionSubmittedAt(OffsetDateTime businessOpinionSubmittedAt) {
        this.businessOpinionSubmittedAt = businessOpinionSubmittedAt;
    }

    public String getLegalActionResult() {
        return legalActionResult;
    }

    public void setLegalActionResult(String legalActionResult) {
        this.legalActionResult = legalActionResult;
    }

    public String getFinalDecisionId() {
        return finalDecisionId;
    }

    public void setFinalDecisionId(String finalDecisionId) {
        this.finalDecisionId = finalDecisionId;
    }

    public String getFinalDecisionReason() {
        return finalDecisionReason;
    }

    public void setFinalDecisionReason(String finalDecisionReason) {
        this.finalDecisionReason = finalDecisionReason;
    }

    public OffsetDateTime getFinalDecisionDecidedAt() {
        return finalDecisionDecidedAt;
    }

    public void setFinalDecisionDecidedAt(OffsetDateTime finalDecisionDecidedAt) {
        this.finalDecisionDecidedAt = finalDecisionDecidedAt;
    }

    public LocalDate getAnnualFeeDueDate() {
        return annualFeeDueDate;
    }

    public void setAnnualFeeDueDate(LocalDate annualFeeDueDate) {
        this.annualFeeDueDate = annualFeeDueDate;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
