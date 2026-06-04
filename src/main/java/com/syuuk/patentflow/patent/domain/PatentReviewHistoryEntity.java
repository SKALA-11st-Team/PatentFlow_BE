package com.syuuk.patentflow.patent.domain;

import com.syuuk.patentflow.common.domain.BaseEntity;
import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import com.syuuk.patentflow.patent.dto.LegalActionResult;
import com.syuuk.patentflow.patent.dto.Recommendation;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "patent_review_history",
        indexes = {
                @Index(name = "idx_review_history_patent_created", columnList = "patent_id, created_at"),
                @Index(name = "idx_review_history_status", columnList = "review_workflow_status"),
                @Index(name = "idx_review_history_department", columnList = "department_id"),
                @Index(name = "idx_review_history_department_status", columnList = "department_id, review_workflow_status"),
                @Index(name = "idx_review_history_department_opinion", columnList = "department_id, business_opinion_decision")
        },
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
    @Column(name = "ai_recommendation", length = 64)
    private Recommendation aiRecommendation;

    @Column(name = "ai_report_id", length = 128)
    private String aiReportId;

    @Column(name = "ai_report_created_at")
    private OffsetDateTime aiReportCreatedAt;

    @Column(name = "ai_recommendation_reason", length = 2000)
    private String aiRecommendationReason;

    @Column(name = "ai_total_score")
    private Integer aiTotalScore;

    @Column(name = "ai_scores_json", columnDefinition = "TEXT")
    private String aiScoresJson;

    @Column(name = "ai_missing_information_json", columnDefinition = "TEXT")
    private String aiMissingInformationJson;

    @Column(name = "ai_report_markdown", columnDefinition = "TEXT")
    private String aiReportMarkdown;

    @Column(name = "ai_report_markdown_path", length = 1000)
    private String aiReportMarkdownPath;

    @Column(name = "summary_text", length = 2000)
    private String summaryText;

    @Column(name = "summary_problem_solved", length = 2000)
    private String summaryProblemSolved;

    @Column(name = "summary_core_technical_points_json", columnDefinition = "TEXT")
    private String summaryCoreTechnicalPointsJson;

    @Column(name = "summary_claims", length = 2000)
    private String summaryClaims;

    @Column(name = "summary_missing_fields_json", columnDefinition = "TEXT")
    private String summaryMissingFieldsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_opinion_decision", length = 64)
    private BusinessOpinionDecision businessOpinionDecision;

    @Column(name = "business_opinion_reason", length = 2000)
    private String businessOpinionReason;

    @Column(name = "business_opinion_submitted_by", length = 128)
    private String businessOpinionSubmittedBy;  // 의견 제출자 이름

    @Column(name = "business_opinion_submitted_at")
    private OffsetDateTime businessOpinionSubmittedAt;

    // 사업부 체크리스트 결과 — 항목별 점수 합산
    @Column(name = "business_checklist_total")
    private Integer businessChecklistTotal;

    // 사업부 정성 평가 점수
    @Column(name = "business_qualitative_score")
    private Integer businessQualitativeScore;

    // 체크리스트 항목별 점수 JSON 직렬화 저장
    @Column(name = "business_checklist_scores_json", columnDefinition = "TEXT")
    private String businessChecklistScoresJson;

    // 체크리스트 제출 시점의 AI 레포트 스냅샷 — 사업부가 참고한 AI 정보 이력 보존
    @Column(name = "business_ai_report_created_at")
    private OffsetDateTime businessAiReportCreatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_ai_recommendation", length = 64)
    private Recommendation businessAiRecommendation;

    @Column(name = "business_ai_total_score")
    private Integer businessAiTotalScore;

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

    public Recommendation getAiRecommendation() {
        return aiRecommendation;
    }

    public void setAiRecommendation(Recommendation aiRecommendation) {
        this.aiRecommendation = aiRecommendation;
    }

    public String getAiReportId() {
        return aiReportId;
    }

    public void setAiReportId(String aiReportId) {
        this.aiReportId = aiReportId;
    }

    public OffsetDateTime getAiReportCreatedAt() {
        return aiReportCreatedAt;
    }

    public void setAiReportCreatedAt(OffsetDateTime aiReportCreatedAt) {
        this.aiReportCreatedAt = aiReportCreatedAt;
    }

    public String getAiRecommendationReason() {
        return aiRecommendationReason;
    }

    public void setAiRecommendationReason(String aiRecommendationReason) {
        this.aiRecommendationReason = aiRecommendationReason;
    }

    public Integer getAiTotalScore() {
        return aiTotalScore;
    }

    public void setAiTotalScore(Integer aiTotalScore) {
        this.aiTotalScore = aiTotalScore;
    }

    public String getAiScoresJson() {
        return aiScoresJson;
    }

    public void setAiScoresJson(String aiScoresJson) {
        this.aiScoresJson = aiScoresJson;
    }

    public String getAiMissingInformationJson() {
        return aiMissingInformationJson;
    }

    public void setAiMissingInformationJson(String aiMissingInformationJson) {
        this.aiMissingInformationJson = aiMissingInformationJson;
    }

    public String getAiReportMarkdown() {
        return aiReportMarkdown;
    }

    public void setAiReportMarkdown(String aiReportMarkdown) {
        this.aiReportMarkdown = aiReportMarkdown;
    }

    public String getAiReportMarkdownPath() {
        return aiReportMarkdownPath;
    }

    public void setAiReportMarkdownPath(String aiReportMarkdownPath) {
        this.aiReportMarkdownPath = aiReportMarkdownPath;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public String getSummaryProblemSolved() {
        return summaryProblemSolved;
    }

    public void setSummaryProblemSolved(String summaryProblemSolved) {
        this.summaryProblemSolved = summaryProblemSolved;
    }

    public String getSummaryCoreTechnicalPointsJson() {
        return summaryCoreTechnicalPointsJson;
    }

    public void setSummaryCoreTechnicalPointsJson(String summaryCoreTechnicalPointsJson) {
        this.summaryCoreTechnicalPointsJson = summaryCoreTechnicalPointsJson;
    }

    public String getSummaryClaims() {
        return summaryClaims;
    }

    public void setSummaryClaims(String summaryClaims) {
        this.summaryClaims = summaryClaims;
    }

    public String getSummaryMissingFieldsJson() {
        return summaryMissingFieldsJson;
    }

    public void setSummaryMissingFieldsJson(String summaryMissingFieldsJson) {
        this.summaryMissingFieldsJson = summaryMissingFieldsJson;
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

    public String getBusinessOpinionSubmittedBy() {
        return businessOpinionSubmittedBy;
    }

    public void setBusinessOpinionSubmittedBy(String businessOpinionSubmittedBy) {
        this.businessOpinionSubmittedBy = businessOpinionSubmittedBy;
    }

    public OffsetDateTime getBusinessOpinionSubmittedAt() {
        return businessOpinionSubmittedAt;
    }

    public void setBusinessOpinionSubmittedAt(OffsetDateTime businessOpinionSubmittedAt) {
        this.businessOpinionSubmittedAt = businessOpinionSubmittedAt;
    }

    public Integer getBusinessChecklistTotal() {
        return businessChecklistTotal;
    }

    public void setBusinessChecklistTotal(Integer businessChecklistTotal) {
        this.businessChecklistTotal = businessChecklistTotal;
    }

    public Integer getBusinessQualitativeScore() {
        return businessQualitativeScore;
    }

    public void setBusinessQualitativeScore(Integer businessQualitativeScore) {
        this.businessQualitativeScore = businessQualitativeScore;
    }

    public String getBusinessChecklistScoresJson() {
        return businessChecklistScoresJson;
    }

    public void setBusinessChecklistScoresJson(String businessChecklistScoresJson) {
        this.businessChecklistScoresJson = businessChecklistScoresJson;
    }

    public OffsetDateTime getBusinessAiReportCreatedAt() {
        return businessAiReportCreatedAt;
    }

    public void setBusinessAiReportCreatedAt(OffsetDateTime businessAiReportCreatedAt) {
        this.businessAiReportCreatedAt = businessAiReportCreatedAt;
    }

    public Recommendation getBusinessAiRecommendation() {
        return businessAiRecommendation;
    }

    public void setBusinessAiRecommendation(Recommendation businessAiRecommendation) {
        this.businessAiRecommendation = businessAiRecommendation;
    }

    public Integer getBusinessAiTotalScore() {
        return businessAiTotalScore;
    }

    public void setBusinessAiTotalScore(Integer businessAiTotalScore) {
        this.businessAiTotalScore = businessAiTotalScore;
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
