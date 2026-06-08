package com.syuuk.patentflow.business.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.business.dto.BusinessChecklistItemResponse;
import com.syuuk.patentflow.business.dto.BusinessChecklistResponseDto;
import com.syuuk.patentflow.business.dto.BusinessChecklistScoreOptionResponse;
import com.syuuk.patentflow.business.dto.BusinessChecklistSubmissionRequest;
import com.syuuk.patentflow.business.dto.BusinessSubmissionChecklistScoreResponse;
import com.syuuk.patentflow.business.dto.BusinessSubmissionVersionResponse;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.patent.domain.PatentReviewHistoryEntity;
import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import com.syuuk.patentflow.patent.dto.Recommendation;
import com.syuuk.patentflow.notification.service.NotificationService;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import com.syuuk.patentflow.patent.service.PatentReviewService;
import com.syuuk.patentflow.settings.repository.QuarterSettingRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessFixtureService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int SUBMISSION_VERSION = 1;
    private static final List<BusinessChecklistItemResponse> CHECKLIST_ITEMS = List.of(
            checklistItem(
                    "TECH_COMPLETENESS",
                    "기술적 가치",
                    "기술완성도",
                    "회사가 특허 관련 기술을 얼마나 구현해 놓은 상태인지 평가",
                    "판매 가능한 수준으로 개발 완료",
                    "테스트용 제품 개발 완료",
                    "테스트용 제품 개발 진행 중",
                    "아이디어 상태"),
            checklistItem(
                    "TECH_ORIGINALITY",
                    "기술적 가치",
                    "기술 독창성",
                    "기존 기술 대비 얼마나 뛰어난 기술인지 평가",
                    "타사 대비 독창적이고 최고 수준",
                    "타사와 유사하거나 약간 개량",
                    "동일 기능이나 기술 수준은 낮음",
                    "종래기술의 단순 조합 수준"),
            checklistItem(
                    "MARKETABILITY",
                    "경제적 가치",
                    "시장성",
                    "국내 및 해외 경쟁사가 유사 분야의 사업을 진행할 가능성 평가",
                    "국내외 경쟁사 사업 진행 가능성 높음",
                    "국내 경쟁사 사업 진행 가능성 높음",
                    "당사만 관련 사업 진행",
                    "관련 사업 진행 회사 없음"),
            checklistItem(
                    "EXPECTED_EFFECT",
                    "경제적 가치",
                    "기대효과",
                    "기술보호, 수익창출, 비용절감에 기여하는 정도 평가",
                    "기술보호, 수익창출, 비용절감 모두 기여",
                    "세 가지 중 두 가지에 기여",
                    "세 가지 중 한 가지에 기여",
                    "특허 기여도 없음"));

    private final PatentReviewService patentReviewService;
    private final PatentReviewHistoryRepository reviewHistoryRepository;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final QuarterSettingRepository quarterSettingRepository;

    public BusinessFixtureService(
            PatentReviewService patentReviewService,
            PatentReviewHistoryRepository reviewHistoryRepository,
            ObjectMapper objectMapper,
            NotificationService notificationService,
            QuarterSettingRepository quarterSettingRepository
    ) {
        this.patentReviewService = patentReviewService;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.quarterSettingRepository = quarterSettingRepository;
    }

    /**
     * @relatedFR FR-BUS-01
     * @relatedUI UI-LEGAL-04, UI-BUS-03
     * @description 사업부 평가 체크리스트 정의를 조회한다.
     */
    public List<BusinessChecklistItemResponse> getChecklistItems() {
        return CHECKLIST_ITEMS;
    }

    /**
     * @relatedFR FR-BUS-01, FR-LEGAL-11
     * @relatedUI UI-BUS-04, UI-BUS-05
     * @description 특허별 사업부 제출 이력을 조회한다.
     */
    @Transactional
    public List<BusinessSubmissionVersionResponse> getSubmissions(String patentId) {
        PatentDetailResponse patent = patentReviewService.getPatentDetail(patentId);
        List<PatentReviewHistoryEntity> histories = reviewHistoryRepository.findByPatentIdOrderByCreatedAtDesc(patentId);
        for (PatentReviewHistoryEntity history : histories) {
            if (history.getBusinessOpinionDecision() != null && history.getBusinessOpinionSubmittedAt() != null) {
                return List.of(toResponse(history));
            }
        }

        if (patent.businessOpinion().decision() == null || patent.businessOpinion().submittedAt() == null) {
            return List.of();
        }

        return List.of(toSeedVersion(patent));
    }

    /**
     * @relatedFR FR-BUS-01
     * @relatedUI UI-LEGAL-04, UI-BUS-03
     * @description 사업부 의견/체크리스트 제출을 저장하고 제출 이력 응답을 반환한다.
     */
    @Transactional
    public BusinessSubmissionVersionResponse submit(String patentId, BusinessChecklistSubmissionRequest request) {
        patentReviewService.ensurePatentExists(patentId);
        validateChecklistResponses(request);
        String quarterKey = quarterSettingRepository.findAll().stream()
                .filter(q -> q.isActivated() && !q.isEnded())
                .findFirst()
                .or(() -> quarterSettingRepository.findAll().stream().filter(q -> q.isActivated()).findFirst())
                .map(q -> q.getQuarterKey())
                .orElse(null);
        OffsetDateTime submittedAt = OffsetDateTime.now(KST);
        BusinessSubmissionVersionResponse submission = toVersion(patentId, request, submittedAt);
        patentReviewService.recordBusinessOpinion(
                patentId,
                request.finalOpinion(),
                valueOrDefault(request.finalReason(), defaultReason(request.finalOpinion())),
                submittedAt);
        PatentReviewHistoryEntity history = findBusinessSubmissionState(patentId, quarterKey);
        applySubmission(history, submission);
        reviewHistoryRepository.save(history);
        PatentDetailResponse patent = patentReviewService.getPatentDetail(patentId);
        String deptName = patent.departmentName() != null ? patent.departmentName() : "사업부";
        notificationService.addNotification(
                "사업부 의견 수신",
                deptName + "에서 " + patent.title() + " 특허에 대한 의견을 제출했습니다.",
                "ADMIN",
                "/admin/patents/" + patentId);
        return submission;
    }

    private BusinessSubmissionVersionResponse toResponse(PatentReviewHistoryEntity entity) {
        return new BusinessSubmissionVersionResponse(
                submissionId(entity.getPatentId()),
                SUBMISSION_VERSION,
                entity.getBusinessOpinionDecision(),
                entity.getBusinessOpinionReason(),
                entity.getBusinessOpinionSubmittedBy(),
                entity.getBusinessOpinionSubmittedAt(),
                entity.getBusinessAiReportCreatedAt(),
                entity.getBusinessAiRecommendation(),
                valueOrZero(entity.getBusinessAiTotalScore()),
                valueOrZero(entity.getBusinessChecklistTotal()),
                readChecklistScores(entity.getBusinessChecklistScoresJson()),
                valueOrZero(entity.getBusinessQualitativeScore()));
    }

    private void applySubmission(PatentReviewHistoryEntity history, BusinessSubmissionVersionResponse submission) {
        history.setBusinessOpinionDecision(submission.decision());
        history.setBusinessOpinionReason(submission.reason());
        history.setBusinessOpinionSubmittedBy(submission.submittedBy());
        history.setBusinessOpinionSubmittedAt(submission.submittedAt());
        history.setBusinessAiReportCreatedAt(submission.aiReportCreatedAt());
        history.setBusinessAiRecommendation(submission.aiRecommendation());
        history.setBusinessAiTotalScore(submission.aiTotalScore());
        history.setBusinessChecklistTotal(submission.checklistTotal());
        history.setBusinessQualitativeScore(submission.qualitativeScore());
        history.setBusinessChecklistScoresJson(writeChecklistScores(submission.checklistScores()));
    }

    private PatentReviewHistoryEntity findBusinessSubmissionState(String patentId, String quarterKey) {
        if (quarterKey != null) {
            return reviewHistoryRepository.findByPatentIdAndQuarterKey(patentId, quarterKey)
                    .orElseGet(() -> new PatentReviewHistoryEntity(patentId, quarterKey));
        }
        List<PatentReviewHistoryEntity> histories = reviewHistoryRepository.findByPatentIdOrderByCreatedAtDesc(patentId);
        if (!histories.isEmpty()) {
            return histories.get(0);
        }
        return new PatentReviewHistoryEntity(patentId, "UNQUARTERED");
    }

    private List<BusinessSubmissionChecklistScoreResponse> readChecklistScores(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("사업부 체크리스트 점수 JSON을 읽을 수 없습니다.", exception);
        }
    }

    private String writeChecklistScores(List<BusinessSubmissionChecklistScoreResponse> scores) {
        try {
            return objectMapper.writeValueAsString(scores);
        } catch (Exception exception) {
            throw new IllegalStateException("사업부 체크리스트 점수 JSON을 저장할 수 없습니다.", exception);
        }
    }

    private BusinessSubmissionVersionResponse toVersion(
            String patentId,
            BusinessChecklistSubmissionRequest request,
            OffsetDateTime submittedAt
    ) {
        List<BusinessSubmissionChecklistScoreResponse> checklistScores = request.responses().stream()
                .map(response -> new BusinessSubmissionChecklistScoreResponse(
                        response.itemId(),
                        response.score() == null ? 0 : response.score(),
                        response.memo()))
                .toList();
        int checklistTotal = checklistScores.stream()
                .mapToInt(BusinessSubmissionChecklistScoreResponse::score)
                .sum() + request.qualitativeScore();
        Recommendation recommendation = patentReviewService.getCurrentRecommendation(patentId);
        Integer aiTotalScore = patentReviewService.getAiTotalScore(patentId);

        return new BusinessSubmissionVersionResponse(
                submissionId(patentId),
                SUBMISSION_VERSION,
                request.finalOpinion(),
                valueOrDefault(request.finalReason(), defaultReason(request.finalOpinion())),
                valueOrDefault(request.evaluatorName(), "사업부 담당자"),
                submittedAt,
                patentReviewService.getAiReportCreatedAt(patentId),
                recommendation,
                aiTotalScore == null ? 0 : aiTotalScore,
                checklistTotal,
                checklistScores,
                request.qualitativeScore());
    }

    private BusinessSubmissionVersionResponse toSeedVersion(PatentDetailResponse patent) {
        BusinessOpinionDecision decision = patent.businessOpinion().decision();
        int qualitativeScore = decision == BusinessOpinionDecision.MAINTAIN ? 3 : -3;

        return new BusinessSubmissionVersionResponse(
                "%s-SUB-01".formatted(patent.patentId()),
                SUBMISSION_VERSION,
                decision,
                valueOrDefault(patent.businessOpinion().reason(), defaultReason(decision)),
                patent.departmentName() + " 담당자",
                patent.businessOpinion().submittedAt(),
                patent.aiEvaluationReport().createdAt(),
                patent.currentRecommendation(),
                patent.aiEvaluationReport().totalScore() == null
                        ? 0
                        : patent.aiEvaluationReport().totalScore(),
                qualitativeScore,
                List.of(),
                qualitativeScore);
    }

    private String submissionId(String patentId) {
        return "%s-SUB-01".formatted(patentId);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private void validateChecklistResponses(BusinessChecklistSubmissionRequest request) {
        Set<String> definedIds = CHECKLIST_ITEMS.stream()
                .map(BusinessChecklistItemResponse::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> submittedIds = new LinkedHashSet<>();
        for (BusinessChecklistResponseDto response : request.responses()) {
            if (!definedIds.contains(response.itemId()) || !submittedIds.add(response.itemId())) {
                throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "체크리스트 응답은 정의된 항목을 중복 없이 제출해야 합니다.");
            }
        }
        if (!submittedIds.equals(definedIds)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "체크리스트 응답은 모든 정의 항목을 포함해야 합니다.");
        }
    }

    private static BusinessChecklistItemResponse checklistItem(
            String id,
            String category,
            String title,
            String description,
            String score4,
            String score3,
            String score2,
            String score1
    ) {
        return new BusinessChecklistItemResponse(
                id,
                category,
                title,
                description,
                List.of(
                        new BusinessChecklistScoreOptionResponse(4, score4),
                        new BusinessChecklistScoreOptionResponse(3, score3),
                        new BusinessChecklistScoreOptionResponse(2, score2),
                        new BusinessChecklistScoreOptionResponse(1, score1)));
    }

    private String defaultReason(BusinessOpinionDecision opinion) {
        return opinion == BusinessOpinionDecision.MAINTAIN
                ? "사업부 검토 결과 유지 의견을 제출했습니다."
                : "사업부 검토 결과 포기 의견을 제출했습니다.";
    }

    private String valueOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
