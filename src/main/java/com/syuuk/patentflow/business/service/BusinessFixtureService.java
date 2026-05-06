package com.syuuk.patentflow.business.service;

import com.syuuk.patentflow.business.dto.BusinessChecklistItemResponse;
import com.syuuk.patentflow.business.dto.BusinessChecklistResponseDto;
import com.syuuk.patentflow.business.dto.BusinessChecklistScoreOptionResponse;
import com.syuuk.patentflow.business.dto.BusinessChecklistSubmissionRequest;
import com.syuuk.patentflow.business.dto.BusinessSubmissionChecklistScoreResponse;
import com.syuuk.patentflow.business.dto.BusinessSubmissionVersionResponse;
import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import com.syuuk.patentflow.patent.dto.Recommendation;
import com.syuuk.patentflow.patent.service.PatentFixtureService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BusinessFixtureService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PatentFixtureService patentFixtureService;
    private final Map<String, List<BusinessSubmissionVersionResponse>> submissions = new LinkedHashMap<>();

    public BusinessFixtureService(PatentFixtureService patentFixtureService) {
        this.patentFixtureService = patentFixtureService;
    }

    /**
     * @relatedFR FR-009
     * @relatedUI UI-005, UI-006
     * @description 사업부 평가 체크리스트 정의를 조회한다.
     */
    public List<BusinessChecklistItemResponse> getChecklistItems() {
        return List.of(
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
    }

    /**
     * @relatedFR FR-009, FR-013
     * @relatedUI UI-009
     * @description 특허별 사업부 제출 이력을 조회한다.
     */
    public List<BusinessSubmissionVersionResponse> getSubmissions(String patentId) {
        PatentDetailResponse patent = patentFixtureService.getPatentDetail(patentId);
        List<BusinessSubmissionVersionResponse> patentSubmissions = submissions.get(patentId);
        if (patentSubmissions != null) {
            return patentSubmissions;
        }

        if (patent.businessOpinion().decision() == null || patent.businessOpinion().submittedAt() == null) {
            return List.of();
        }

        List<BusinessSubmissionVersionResponse> seededSubmissions = new ArrayList<>();
        seededSubmissions.add(toSeedVersion(patent));
        submissions.put(patentId, seededSubmissions);
        return seededSubmissions;
    }

    /**
     * @relatedFR FR-009
     * @relatedUI UI-005, UI-006
     * @description 사업부 의견/체크리스트 제출을 저장하고 FE 입력 payload를 그대로 반환한다.
     */
    public BusinessChecklistSubmissionRequest submit(String patentId, BusinessChecklistSubmissionRequest request) {
        patentFixtureService.ensurePatentExists(patentId);
        List<BusinessSubmissionVersionResponse> patentSubmissions = submissions.computeIfAbsent(
                patentId,
                key -> new ArrayList<>());
        int version = patentSubmissions.size() + 1;
        OffsetDateTime submittedAt = OffsetDateTime.now(KST);
        patentSubmissions.add(toVersion(patentId, request, version, submittedAt));
        patentFixtureService.recordBusinessOpinion(
                patentId,
                request.finalOpinion(),
                valueOrDefault(request.finalReason(), defaultReason(request.finalOpinion())),
                submittedAt);
        return request;
    }

    private BusinessSubmissionVersionResponse toVersion(
            String patentId,
            BusinessChecklistSubmissionRequest request,
            int version,
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
        Recommendation recommendation = patentFixtureService.getCurrentRecommendation(patentId);
        Integer aiTotalScore = patentFixtureService.getAiTotalScore(patentId);

        return new BusinessSubmissionVersionResponse(
                "%s-SUB-%02d".formatted(patentId, version),
                version,
                request.finalOpinion(),
                valueOrDefault(request.finalReason(), defaultReason(request.finalOpinion())),
                valueOrDefault(request.evaluatorName(), "사업부 담당자"),
                submittedAt,
                patentFixtureService.getAiReportCreatedAt(patentId),
                recommendation,
                aiTotalScore == null ? 0 : aiTotalScore,
                checklistTotal,
                checklistScores,
                request.qualitativeScore());
    }

    private BusinessSubmissionVersionResponse toSeedVersion(PatentDetailResponse patent) {
        BusinessOpinionDecision decision = patent.businessOpinion().decision();
        int qualitativeScore = decision == BusinessOpinionDecision.MAINTAIN ? 3 : -3;
        List<BusinessSubmissionChecklistScoreResponse> checklistScores = getChecklistItems().stream()
                .map(item -> new BusinessSubmissionChecklistScoreResponse(
                        item.id(),
                        decision == BusinessOpinionDecision.MAINTAIN ? 3 : 2,
                        "%s 기준으로 기존 제출 의견을 복원했습니다.".formatted(item.title())))
                .toList();
        int checklistTotal = checklistScores.stream()
                .mapToInt(BusinessSubmissionChecklistScoreResponse::score)
                .sum() + qualitativeScore;

        return new BusinessSubmissionVersionResponse(
                "%s-SUB-01".formatted(patent.patentId()),
                1,
                decision,
                valueOrDefault(patent.businessOpinion().reason(), defaultReason(decision)),
                patent.departmentName() + " 담당자",
                patent.businessOpinion().submittedAt(),
                patent.aiEvaluationReport().createdAt(),
                patent.currentRecommendation(),
                patent.aiEvaluationReport().totalScore() == null
                        ? 0
                        : patent.aiEvaluationReport().totalScore(),
                checklistTotal,
                checklistScores,
                qualitativeScore);
    }

    private BusinessChecklistItemResponse checklistItem(
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
