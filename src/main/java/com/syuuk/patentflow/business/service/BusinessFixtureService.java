package com.syuuk.patentflow.business.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.business.dto.BusinessChecklistItemResponse;
import com.syuuk.patentflow.business.dto.BusinessChecklistResponseDto;
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

    private final PatentReviewService patentReviewService;
    private final PatentReviewHistoryRepository reviewHistoryRepository;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final QuarterSettingRepository quarterSettingRepository;
    private final BusinessChecklistItemService checklistItemService;

    public BusinessFixtureService(
            PatentReviewService patentReviewService,
            PatentReviewHistoryRepository reviewHistoryRepository,
            ObjectMapper objectMapper,
            NotificationService notificationService,
            QuarterSettingRepository quarterSettingRepository,
            BusinessChecklistItemService checklistItemService
    ) {
        this.patentReviewService = patentReviewService;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.quarterSettingRepository = quarterSettingRepository;
        this.checklistItemService = checklistItemService;
    }

    /**
     * @relatedFR FR-BUS-01
     * @relatedUI UI-LEGAL-04, UI-BUS-03
     * @description 사업부 평가 체크리스트 정의를 조회한다.
     *     하드코딩이던 항목을 DB로 이전해 리걸팀이 설정 화면에서 변경할 수 있다.
     */
    public List<BusinessChecklistItemResponse> getChecklistItems() {
        return checklistItemService.getItems();
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
    public BusinessSubmissionVersionResponse submit(
            String patentId, BusinessChecklistSubmissionRequest request, String actor) {
        patentReviewService.ensurePatentExists(patentId);
        validateChecklistResponses(request);
        String quarterKey = quarterSettingRepository.findAll().stream()
                .filter(q -> q.isActivated() && !q.isEnded())
                .findFirst()
                .or(() -> quarterSettingRepository.findAll().stream().filter(q -> q.isActivated()).findFirst())
                .map(q -> q.getQuarterKey())
                .orElse(null);
        OffsetDateTime submittedAt = OffsetDateTime.now(KST);
        BusinessSubmissionVersionResponse submission = toVersion(patentId, request, submittedAt, actor);
        patentReviewService.recordBusinessOpinion(
                patentId,
                request.finalOpinion(),
                valueOrDefault(request.finalReason(), defaultReason(request.finalOpinion())),
                submittedAt);
        PatentReviewHistoryEntity history = findBusinessSubmissionState(patentId, quarterKey);
        applySubmission(history, submission);
        // 제출 시점에 사업부가 실제로 본 레포트(유효본 전체)를 보존한다 — 이후 법무 편집/재생성이
        // ai_* 컬럼을 바꿔도 "사업부가 어떤 레포트를 보고 의견을 냈는지"는 복원 가능해야 한다.
        history.setBusinessAiReportSnapshotJson(
                writeAiReportSnapshot(patentReviewService.getPatentDetail(patentId).aiEvaluationReport()));
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
        // 체크리스트/스냅샷은 의견(recordBusinessOpinion)이 기록된 '최신 행'과 같은 행에 저장해야 한다.
        // 과거에는 활성 분기 키로 행을 찾거나 새로 만들었는데, 최신 행과 분기 키 행이 다르면
        // ai_*가 전부 null인 빈 행이 새로 생성·최신화되어 레포트/워크플로우 상태가 화면에서
        // 사라지고 제출 데이터가 두 행에 분산되는 결함이 있었다(E2E에서 실증된 H-1).
        List<PatentReviewHistoryEntity> histories = reviewHistoryRepository.findByPatentIdOrderByCreatedAtDesc(patentId);
        if (!histories.isEmpty()) {
            return histories.get(0);
        }
        // 제출은 WAITING_BUSINESS_RESPONSE 상태에서만 가능하므로 실제로는 도달하지 않는 안전 폴백.
        return new PatentReviewHistoryEntity(patentId, quarterKey != null ? quarterKey : "UNQUARTERED");
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

    private String writeAiReportSnapshot(Object report) {
        try {
            return report == null ? null : objectMapper.writeValueAsString(report);
        } catch (Exception exception) {
            throw new IllegalStateException("사업부 제출 시점 AI 레포트 스냅샷을 저장할 수 없습니다.", exception);
        }
    }

    private BusinessSubmissionVersionResponse toVersion(
            String patentId,
            BusinessChecklistSubmissionRequest request,
            OffsetDateTime submittedAt,
            String actor
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
        // CONTRACT-02: 사업부 제출 스냅샷의 AI 점수는 0~400 원문 합이 아니라 화면 정본인 0~100 평균을
        // 사용해 다른 화면(AI 레포트 카드)과 척도를 통일한다.
        Integer aiAverageScore = patentReviewService.getAiAverageScore(patentId);

        return new BusinessSubmissionVersionResponse(
                submissionId(patentId),
                SUBMISSION_VERSION,
                request.finalOpinion(),
                valueOrDefault(request.finalReason(), defaultReason(request.finalOpinion())),
                // BIZ-09: 제출자명을 클라이언트 입력(evaluatorName)이 아니라 인증 주체로 기록(검증된 작성자 추적).
                valueOrDefault(actor, valueOrDefault(request.evaluatorName(), "사업부 담당자")),
                submittedAt,
                patentReviewService.getAiReportCreatedAt(patentId),
                recommendation,
                aiAverageScore == null ? 0 : aiAverageScore,
                checklistTotal,
                checklistScores,
                request.qualitativeScore());
    }

    private BusinessSubmissionVersionResponse toSeedVersion(PatentDetailResponse patent) {
        BusinessOpinionDecision decision = patent.businessOpinion().decision();
        int qualitativeScore = decision == BusinessOpinionDecision.MAINTAIN ? 3 : -3;
        // CONTRACT-02: 라이브 toVersion 경로와 동일하게 aiTotalScore는 0~400 원문 합(totalScore)이 아니라
        // 0~100 평균(getAiAverageScore)을 사용해 척도를 통일한다(AI 점수 0~100과 체크리스트 1~4 혼동 방지).
        Integer aiAverageScore = patentReviewService.getAiAverageScore(patent.patentId());

        return new BusinessSubmissionVersionResponse(
                "%s-SUB-01".formatted(patent.patentId()),
                SUBMISSION_VERSION,
                decision,
                valueOrDefault(patent.businessOpinion().reason(), defaultReason(decision)),
                patent.departmentName() + " 담당자",
                patent.businessOpinion().submittedAt(),
                patent.aiEvaluationReport().createdAt(),
                patent.currentRecommendation(),
                aiAverageScore == null ? 0 : aiAverageScore,
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
        Set<String> definedIds = checklistItemService.getItems().stream()
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
