package com.syuuk.patentflow.patent.domain;

import com.syuuk.patentflow.common.domain.BaseEntity;
import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import com.syuuk.patentflow.patent.dto.LegalActionResult;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "patent_review_history",
        uniqueConstraints = @UniqueConstraint(columnNames = {"patent_id", "quarter_key"}))
public class PatentReviewHistoryEntity extends BaseEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "patent_id", nullable = false, length = 32)
    private String patentId;

    @Column(name = "quarter_key", nullable = false, length = 16)
    private String quarterKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_workflow_status", length = 64)
    private ReviewWorkflowStatus reviewWorkflowStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_opinion_decision", length = 64)
    private BusinessOpinionDecision businessOpinionDecision;

    @Column(name = "business_opinion_reason", length = 2000)
    private String businessOpinionReason;

    @Column(name = "business_opinion_submitted_at")
    private OffsetDateTime businessOpinionSubmittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "legal_action_result", length = 64)
    private LegalActionResult legalActionResult;

    @Column(name = "final_decision_id", length = 64)
    private String finalDecisionId;

    @Column(name = "final_decision_reason", length = 2000)
    private String finalDecisionReason;

    @Column(name = "final_decision_decided_at")
    private OffsetDateTime finalDecisionDecidedAt;

    @Column(name = "annual_fee_due_date")
    private LocalDate annualFeeDueDate;

    @Column(name = "department_id", length = 64)
    private String departmentId;

    @Column(name = "department_name", length = 128)
    private String departmentName;

    protected PatentReviewHistoryEntity() {
    }

    public PatentReviewHistoryEntity(String patentId, String quarterKey) {
        this.id = patentId + "|" + quarterKey;
        this.patentId = patentId;
        this.quarterKey = quarterKey;
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

    public ReviewWorkflowStatus getReviewWorkflowStatus() {
        return reviewWorkflowStatus;
    }

    public void setReviewWorkflowStatus(ReviewWorkflowStatus reviewWorkflowStatus) {
        this.reviewWorkflowStatus = reviewWorkflowStatus;
    }

    public BusinessOpinionDecision getBusinessOpinionDecision() {
        return businessOpinionDecision;
    }

    public void setBusinessOpinionDecision(BusinessOpinionDecision businessOpinionDecision) {
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

    public LegalActionResult getLegalActionResult() {
        return legalActionResult;
    }

    public void setLegalActionResult(LegalActionResult legalActionResult) {
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
}
