package com.syuuk.patentflow.patent.domain;

import com.syuuk.patentflow.common.domain.BaseEntity;
import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import com.syuuk.patentflow.patent.dto.CoApplicantConsentStatus;
import com.syuuk.patentflow.patent.dto.LegalActionResult;
import com.syuuk.patentflow.patent.dto.Recommendation;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

    @Convert(converter = RecommendationConverter.class)
    @Column(name = "ai_recommendation", length = 64)
    private Recommendation aiRecommendation;

    @Column(name = "ai_report_id", length = 128)
    private String aiReportId;

    @Column(name = "ai_report_created_at")
    private OffsetDateTime aiReportCreatedAt;

    // LLM 생성 텍스트(요약 markdown이 그대로 들어옴) — varchar(2000) 초과 시 저장 실패하던 결함 수정
    @Column(name = "ai_recommendation_reason", columnDefinition = "TEXT")
    private String aiRecommendationReason;

    @Column(name = "ai_total_score")
    private Integer aiTotalScore;

    @Column(name = "ai_average_score")
    private Double aiAverageScore;

    @Column(name = "ai_final_grade", length = 16)
    private String aiFinalGrade;

    @Column(name = "ai_degraded")
    private Boolean aiDegraded;

    @Column(name = "ai_failure_reason", columnDefinition = "TEXT")
    private String aiFailureReason;

    // xcomp-be-agent-2: Agent 계약 신호(품질 경고 목록 JSON / 근거 신뢰도)를 영속해 재조회 시에도 노출한다.
    @Column(name = "ai_warnings_json", columnDefinition = "TEXT")
    private String aiWarningsJson;

    @Column(name = "ai_evidence_confidence", length = 32)
    private String aiEvidenceConfidence;

    // AIREPORT-RICH: 에이전트가 FE 렌더링용으로 구조화한 필드(요약 카드/섹션별 본문) 영속 — 재조회 시 유실 방지.
    @Column(name = "ai_summary_brief_json", columnDefinition = "TEXT")
    private String aiSummaryBriefJson;

    @Column(name = "ai_report_sections_json", columnDefinition = "TEXT")
    private String aiReportSectionsJson;

    @Column(name = "ai_scores_json", columnDefinition = "TEXT")
    private String aiScoresJson;

    @Column(name = "ai_missing_information_json", columnDefinition = "TEXT")
    private String aiMissingInformationJson;

    // ORCH-06/AIREPORT-02: 리포트 레벨 리치 근거 저장(재조회 시 유실 방지). JSON/긴 텍스트라 TEXT 컬럼.
    @Column(name = "ai_key_evidence", columnDefinition = "TEXT")
    private String aiKeyEvidence;

    @Column(name = "ai_judgement_grounds_json", columnDefinition = "TEXT")
    private String aiJudgementGroundsJson;

    @Column(name = "ai_business_check_requests_json", columnDefinition = "TEXT")
    private String aiBusinessCheckRequestsJson;

    @Column(name = "ai_external_sources_json", columnDefinition = "TEXT")
    private String aiExternalSourcesJson;

    @Column(name = "ai_report_markdown", columnDefinition = "TEXT")
    private String aiReportMarkdown;

    @Column(name = "ai_report_markdown_path", length = 1000)
    private String aiReportMarkdownPath;

    // ── 법무 편집 오버라이드(FR-LEGAL-09: AI 초안과 사람 수정의 분리 보존) ──
    // AI 원본(ai_* 컬럼)은 불변으로 두고, 법무팀이 수정한 필드만 JSON으로 분리 저장한다.
    // 조회 시 ai_* 위에 오버레이되어 '유효 레포트'가 되고, null이면 원본 그대로다.
    @Column(name = "ai_edit_overrides_json", columnDefinition = "TEXT")
    private String aiEditOverridesJson;

    @Column(name = "ai_edited_by", length = 128)
    private String aiEditedBy;

    @Column(name = "ai_edited_at")
    private OffsetDateTime aiEditedAt;

    // 낙관적 락 토큰 — 동시 편집 시 expectedEditVersion 불일치로 409를 돌려준다.
    @Column(name = "ai_edit_version")
    private Integer aiEditVersion;

    // 편집이 기준으로 삼은 레포트 ID. ai_report_id와 다르면 편집 이후 레포트가 재생성된 것(stale).
    @Column(name = "ai_edit_base_report_id", length = 128)
    private String aiEditBaseReportId;

    // 이 레포트 생성에 적용된 가치평가 기준(valuationConfig) 스냅샷 — 기준 변경 이력 추적용.
    @Column(name = "ai_applied_criteria_json", columnDefinition = "TEXT")
    private String aiAppliedCriteriaJson;

    // LLM 생성 요약 — 길이 상한이 없어 TEXT로 저장
    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "summary_problem_solved", columnDefinition = "TEXT")
    private String summaryProblemSolved;

    @Column(name = "summary_core_technical_points_json", columnDefinition = "TEXT")
    private String summaryCoreTechnicalPointsJson;

    @Column(name = "summary_claims", columnDefinition = "TEXT")
    private String summaryClaims;

    @Column(name = "summary_missing_fields_json", columnDefinition = "TEXT")
    private String summaryMissingFieldsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_opinion_decision", length = 64)
    private BusinessOpinionDecision businessOpinionDecision;

    // 사용자 자유 입력 — FE에 길이 제한이 없어 TEXT로 저장
    @Column(name = "business_opinion_reason", columnDefinition = "TEXT")
    private String businessOpinionReason;

    @Column(name = "business_opinion_submitted_by", length = 128)
    private String businessOpinionSubmittedBy;  // 의견 제출자 이름

    @Column(name = "business_opinion_submitted_at")
    private OffsetDateTime businessOpinionSubmittedAt;

    // 공동출원 합의 게이트: 공동출원 특허는 최종 판단 전 공동출원인 합의(AGREED) 필요.
    @Enumerated(EnumType.STRING)
    @Column(name = "co_applicant_consent_status", length = 64)
    private CoApplicantConsentStatus coApplicantConsentStatus;

    @Column(name = "co_applicant_consent_reason", columnDefinition = "TEXT")
    private String coApplicantConsentReason;

    @Column(name = "co_applicant_consent_decided_by", length = 128)
    private String coApplicantConsentDecidedBy;

    @Column(name = "co_applicant_consent_decided_at")
    private OffsetDateTime coApplicantConsentDecidedAt;

    // 사업부 체크리스트 결과 — 항목별 점수 합산
    @Column(name = "business_checklist_total")
    private Integer businessChecklistTotal;

    // 사업부 정성 평가 점수
    @Column(name = "business_qualitative_score")
    private Integer businessQualitativeScore;

    // 체크리스트 항목별 점수 JSON 직렬화 저장
    @Column(name = "business_checklist_scores_json", columnDefinition = "TEXT")
    private String businessChecklistScoresJson;

    // 사업부 정성 메모(자유 서술) — 입력만 받고 폐기되던 것을 영속화
    @Column(name = "business_qualitative_memo", columnDefinition = "TEXT")
    private String businessQualitativeMemo;

    // 사업부가 추가로 확인 필요하다고 적은 사항
    @Column(name = "business_additional_needs", columnDefinition = "TEXT")
    private String businessAdditionalNeeds;

    // 사업부가 입력한 평가일(사용자 입력 문자열)
    @Column(name = "business_evaluated_at", length = 64)
    private String businessEvaluatedAt;

    // 체크리스트 제출 시점의 AI 레포트 스냅샷 — 사업부가 참고한 AI 정보 이력 보존
    @Column(name = "business_ai_report_created_at")
    private OffsetDateTime businessAiReportCreatedAt;

    // 제출 시점에 사업부가 실제로 본 레포트 전체(JSON). 기존 3개 스냅샷 컬럼(createdAt/recommendation/
    // totalScore)만으로는 이후 레포트 편집·재생성 시 사업부가 본 내용을 복원할 수 없던 결함의 수정.
    @Column(name = "business_ai_report_snapshot_json", columnDefinition = "TEXT")
    private String businessAiReportSnapshotJson;

    @Convert(converter = RecommendationConverter.class)
    @Column(name = "business_ai_recommendation", length = 64)
    private Recommendation businessAiRecommendation;

    @Column(name = "business_ai_total_score")
    private Integer businessAiTotalScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "legal_action_result", length = 64)
    private LegalActionResult legalActionResult;

    @Column(name = "final_decision_id", length = 64)
    private String finalDecisionId;

    @Column(name = "final_decision_reason", columnDefinition = "TEXT")
    private String finalDecisionReason;

    @Column(name = "final_decision_decided_at")
    private OffsetDateTime finalDecisionDecidedAt;

    // REVIEW-07: 최종 판단 행위자(인증 주체). 과거 시드/이력은 미상이라 nullable.
    @Column(name = "final_decision_decided_by", length = 128)
    private String finalDecisionDecidedBy;

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

    public Double getAiAverageScore() {
        return aiAverageScore;
    }

    public void setAiAverageScore(Double aiAverageScore) {
        this.aiAverageScore = aiAverageScore;
    }

    public String getAiFinalGrade() {
        return aiFinalGrade;
    }

    public void setAiFinalGrade(String aiFinalGrade) {
        this.aiFinalGrade = aiFinalGrade;
    }

    public Boolean getAiDegraded() {
        return aiDegraded;
    }

    public void setAiDegraded(Boolean aiDegraded) {
        this.aiDegraded = aiDegraded;
    }

    public String getAiFailureReason() {
        return aiFailureReason;
    }

    public void setAiFailureReason(String aiFailureReason) {
        this.aiFailureReason = aiFailureReason;
    }

    public String getAiWarningsJson() {
        return aiWarningsJson;
    }

    public void setAiWarningsJson(String aiWarningsJson) {
        this.aiWarningsJson = aiWarningsJson;
    }

    public String getAiEvidenceConfidence() {
        return aiEvidenceConfidence;
    }

    public void setAiEvidenceConfidence(String aiEvidenceConfidence) {
        this.aiEvidenceConfidence = aiEvidenceConfidence;
    }

    public String getAiSummaryBriefJson() {
        return aiSummaryBriefJson;
    }

    public void setAiSummaryBriefJson(String aiSummaryBriefJson) {
        this.aiSummaryBriefJson = aiSummaryBriefJson;
    }

    public String getAiReportSectionsJson() {
        return aiReportSectionsJson;
    }

    public void setAiReportSectionsJson(String aiReportSectionsJson) {
        this.aiReportSectionsJson = aiReportSectionsJson;
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

    public String getAiKeyEvidence() {
        return aiKeyEvidence;
    }

    public void setAiKeyEvidence(String aiKeyEvidence) {
        this.aiKeyEvidence = aiKeyEvidence;
    }

    public String getAiJudgementGroundsJson() {
        return aiJudgementGroundsJson;
    }

    public void setAiJudgementGroundsJson(String aiJudgementGroundsJson) {
        this.aiJudgementGroundsJson = aiJudgementGroundsJson;
    }

    public String getAiBusinessCheckRequestsJson() {
        return aiBusinessCheckRequestsJson;
    }

    public void setAiBusinessCheckRequestsJson(String aiBusinessCheckRequestsJson) {
        this.aiBusinessCheckRequestsJson = aiBusinessCheckRequestsJson;
    }

    public String getAiExternalSourcesJson() {
        return aiExternalSourcesJson;
    }

    public void setAiExternalSourcesJson(String aiExternalSourcesJson) {
        this.aiExternalSourcesJson = aiExternalSourcesJson;
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

    public String getAiEditOverridesJson() {
        return aiEditOverridesJson;
    }

    public void setAiEditOverridesJson(String aiEditOverridesJson) {
        this.aiEditOverridesJson = aiEditOverridesJson;
    }

    public String getAiEditedBy() {
        return aiEditedBy;
    }

    public void setAiEditedBy(String aiEditedBy) {
        this.aiEditedBy = aiEditedBy;
    }

    public OffsetDateTime getAiEditedAt() {
        return aiEditedAt;
    }

    public void setAiEditedAt(OffsetDateTime aiEditedAt) {
        this.aiEditedAt = aiEditedAt;
    }

    public Integer getAiEditVersion() {
        return aiEditVersion;
    }

    public void setAiEditVersion(Integer aiEditVersion) {
        this.aiEditVersion = aiEditVersion;
    }

    public String getAiEditBaseReportId() {
        return aiEditBaseReportId;
    }

    public void setAiEditBaseReportId(String aiEditBaseReportId) {
        this.aiEditBaseReportId = aiEditBaseReportId;
    }

    public String getAiAppliedCriteriaJson() {
        return aiAppliedCriteriaJson;
    }

    public void setAiAppliedCriteriaJson(String aiAppliedCriteriaJson) {
        this.aiAppliedCriteriaJson = aiAppliedCriteriaJson;
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

    public CoApplicantConsentStatus getCoApplicantConsentStatus() {
        return coApplicantConsentStatus;
    }

    public void setCoApplicantConsentStatus(CoApplicantConsentStatus coApplicantConsentStatus) {
        this.coApplicantConsentStatus = coApplicantConsentStatus;
    }

    public String getCoApplicantConsentReason() {
        return coApplicantConsentReason;
    }

    public void setCoApplicantConsentReason(String coApplicantConsentReason) {
        this.coApplicantConsentReason = coApplicantConsentReason;
    }

    public String getCoApplicantConsentDecidedBy() {
        return coApplicantConsentDecidedBy;
    }

    public void setCoApplicantConsentDecidedBy(String coApplicantConsentDecidedBy) {
        this.coApplicantConsentDecidedBy = coApplicantConsentDecidedBy;
    }

    public OffsetDateTime getCoApplicantConsentDecidedAt() {
        return coApplicantConsentDecidedAt;
    }

    public void setCoApplicantConsentDecidedAt(OffsetDateTime coApplicantConsentDecidedAt) {
        this.coApplicantConsentDecidedAt = coApplicantConsentDecidedAt;
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

    public String getBusinessQualitativeMemo() {
        return businessQualitativeMemo;
    }

    public void setBusinessQualitativeMemo(String businessQualitativeMemo) {
        this.businessQualitativeMemo = businessQualitativeMemo;
    }

    public String getBusinessAdditionalNeeds() {
        return businessAdditionalNeeds;
    }

    public void setBusinessAdditionalNeeds(String businessAdditionalNeeds) {
        this.businessAdditionalNeeds = businessAdditionalNeeds;
    }

    public String getBusinessEvaluatedAt() {
        return businessEvaluatedAt;
    }

    public void setBusinessEvaluatedAt(String businessEvaluatedAt) {
        this.businessEvaluatedAt = businessEvaluatedAt;
    }

    public OffsetDateTime getBusinessAiReportCreatedAt() {
        return businessAiReportCreatedAt;
    }

    public void setBusinessAiReportCreatedAt(OffsetDateTime businessAiReportCreatedAt) {
        this.businessAiReportCreatedAt = businessAiReportCreatedAt;
    }

    public String getBusinessAiReportSnapshotJson() {
        return businessAiReportSnapshotJson;
    }

    public void setBusinessAiReportSnapshotJson(String businessAiReportSnapshotJson) {
        this.businessAiReportSnapshotJson = businessAiReportSnapshotJson;
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

    public String getFinalDecisionDecidedBy() {
        return finalDecisionDecidedBy;
    }

    public void setFinalDecisionDecidedBy(String finalDecisionDecidedBy) {
        this.finalDecisionDecidedBy = finalDecisionDecidedBy;
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
