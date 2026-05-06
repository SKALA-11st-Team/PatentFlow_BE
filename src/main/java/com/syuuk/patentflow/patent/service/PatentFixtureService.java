package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.response.PageInfo;
import com.syuuk.patentflow.common.response.PageResponse;
import com.syuuk.patentflow.patent.client.GooglePatentsLookupClient;
import com.syuuk.patentflow.patent.client.KiprisPatentLookupClient;
import com.syuuk.patentflow.patent.client.PatentLookupQuery;
import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import com.syuuk.patentflow.patent.dto.AiEvaluationReportResponse;
import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import com.syuuk.patentflow.patent.dto.BusinessOpinionResponse;
import com.syuuk.patentflow.patent.dto.EvaluationCategory;
import com.syuuk.patentflow.patent.dto.EvaluationScoreResponse;
import com.syuuk.patentflow.patent.dto.ExecutiveApprovalDecision;
import com.syuuk.patentflow.patent.dto.FinalDecisionRecordResponse;
import com.syuuk.patentflow.patent.dto.LegalActionResult;
import com.syuuk.patentflow.patent.dto.PatentBibliographicInfoResponse;
import com.syuuk.patentflow.patent.dto.PatentContextSuggestionRequest;
import com.syuuk.patentflow.patent.dto.PatentContextSuggestionResponse;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import com.syuuk.patentflow.patent.dto.PatentHistoryResponse;
import com.syuuk.patentflow.patent.dto.PatentLifecycleStatus;
import com.syuuk.patentflow.patent.dto.PatentListItemResponse;
import com.syuuk.patentflow.patent.dto.PatentSummaryResponse;
import com.syuuk.patentflow.patent.dto.PatentUpsertRequest;
import com.syuuk.patentflow.patent.dto.PatentUpsertResponse;
import com.syuuk.patentflow.patent.dto.Recommendation;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class PatentFixtureService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String PATENT_METADATA_PATH = "docs/skax_patents_list.md";
    private static final Set<String> CONTEXT_STOP_WORDS = Set.of("관련", "기술", "시스템", "방법", "특허", "장치");
    private static final Pattern CONTEXT_TOKEN_SPLITTER = Pattern.compile("[^0-9a-z가-힣]+");

    private final List<PatentDetailResponse> patents;
    private final AtomicInteger patentSequence;
    private final KiprisPatentLookupClient kiprisPatentLookupClient;
    private final GooglePatentsLookupClient googlePatentsLookupClient;
    private final PatentMetadataRepository patentMetadataRepository;

    public PatentFixtureService(
            KiprisPatentLookupClient kiprisPatentLookupClient,
            GooglePatentsLookupClient googlePatentsLookupClient,
            PatentMetadataRepository patentMetadataRepository
    ) {
        this.kiprisPatentLookupClient = kiprisPatentLookupClient;
        this.googlePatentsLookupClient = googlePatentsLookupClient;
        this.patentMetadataRepository = patentMetadataRepository;
        seedPatentMetadataIfNeeded();
        this.patents = new ArrayList<>(loadPatentsFromDatabase());
        this.patentSequence = new AtomicInteger(this.patents.size() + 1);
    }

    /**
     * @relatedFR FR-001, FR-002
     * @relatedUI UI-002, UI-003
     * @description FE 우선 연동을 위한 특허 목록 fixture 조회를 제공한다.
     */
    public PageResponse<PatentListItemResponse> getPatents(
            int page,
            int size,
            String keyword,
            String departmentId,
            ReviewWorkflowStatus reviewWorkflowStatus,
            String sort) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 20);
        List<PatentListItemResponse> filtered = patents.stream()
                .map(this::toListItem)
                .filter(item -> matchesKeyword(item, keyword))
                .filter(item -> departmentId == null || departmentId.isBlank()
                        || item.departmentId().equals(departmentId))
                .filter(item -> reviewWorkflowStatus == null || item.reviewWorkflowStatus() == reviewWorkflowStatus)
                .sorted(sortComparator(sort))
                .toList();

        int fromIndex = Math.min((normalizedPage - 1) * normalizedSize, filtered.size());
        int toIndex = Math.min(fromIndex + normalizedSize, filtered.size());
        int totalPages = (int) Math.ceil((double) filtered.size() / normalizedSize);

        return PageResponse.ok(
                filtered.subList(fromIndex, toIndex),
                new PageInfo(normalizedPage, normalizedSize, filtered.size(), totalPages));
    }

    /**
     * @relatedFR FR-005, FR-006, FR-007, FR-008, FR-011, FR-012
     * @relatedUI UI-005
     * @description AI 특허 평가 레포트와 최종 판단을 분리해 특허 상세를 조회한다.
     */
    public PatentDetailResponse getPatentDetail(String patentId) {
        return findPatent(patentId);
    }

    public void ensurePatentExists(String patentId) {
        findPatent(patentId);
    }

    public List<String> applyExecutiveApproval(List<String> patentIds, ExecutiveApprovalDecision decision) {
        OffsetDateTime decidedAt = OffsetDateTime.now(KST);
        return patentIds.stream()
                .map(patentId -> updatePatent(patentId, patent -> withExecutiveApproval(patent, decision, decidedAt)))
                .map(PatentDetailResponse::patentId)
                .toList();
    }

    public Recommendation getCurrentRecommendation(String patentId) {
        return findPatent(patentId).currentRecommendation();
    }

    public Integer getAiTotalScore(String patentId) {
        return findPatent(patentId).aiEvaluationReport().totalScore();
    }

    public OffsetDateTime getAiReportCreatedAt(String patentId) {
        return findPatent(patentId).aiEvaluationReport().createdAt();
    }

    public void recordBusinessOpinion(
            String patentId,
            BusinessOpinionDecision decision,
            String reason,
            OffsetDateTime submittedAt
    ) {
        updatePatent(patentId, patent -> withBusinessOpinion(patent, decision, reason, submittedAt));
    }

    /**
     * @relatedFR FR-013
     * @relatedUI UI-005, UI-009
     * @description 평가/판단 이력을 조회한다.
     */
    public List<PatentHistoryResponse> getPatentHistory(String patentId) {
        getPatentDetail(patentId);
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 5, 6, 10, 0, 0, 0,
                ZoneId.of("Asia/Seoul").getRules().getOffset(java.time.Instant.now()));
        return List.of(
                new PatentHistoryResponse(
                        "HIST-2026-0001",
                        "AI_EVALUATION_CREATED",
                        "AI 평가 레포트 생성",
                        "1차 AI 특허 평가 레포트가 생성되었습니다.",
                        "AI Evaluation Service",
                        createdAt),
                new PatentHistoryResponse(
                        "HIST-2026-0002",
                        "HUMAN_DECISION_UPDATED",
                        "최종 판단 기록",
                        "관리자 최종 판단 대기 상태로 이력이 기록되었습니다.",
                        "관리자",
                        createdAt.plusHours(1)));
    }

    /**
     * @relatedFR FR-003
     * @relatedUI UI-004
     * @description 공식 metadata fixture에서 관리번호/출원번호/등록번호 기반 외부 검색 결과를 제공한다.
     */
    public PatentBibliographicInfoResponse lookupBibliographicInfo(String managementNumber, String sourcePriority) {
        if (managementNumber == null || managementNumber.isBlank()) {
            return null;
        }

        String keyword = managementNumber.trim().toLowerCase(Locale.ROOT);
        PatentDetailResponse fixturePatent = patents.stream()
                .filter(patent -> lowerEquals(patent.managementNumber(), keyword)
                        || lowerEquals(patent.applicationNumber(), keyword)
                        || lowerEquals(patent.registrationNumber(), keyword))
                .findFirst()
                .orElse(null);
        PatentLookupQuery query = new PatentLookupQuery(
                managementNumber.trim(),
                fixturePatent == null ? managementNumber.trim() : fixturePatent.applicationNumber(),
                fixturePatent == null ? managementNumber.trim() : fixturePatent.registrationNumber(),
                fixturePatent == null ? "KR" : fixturePatent.country());

        for (String source : lookupPriority(sourcePriority)) {
            PatentBibliographicInfoResponse externalResult = switch (source) {
                case "KIPRIS" -> kiprisPatentLookupClient.lookup(query).orElse(null);
                case "GOOGLE_PATENTS" -> googlePatentsLookupClient.lookup(query).orElse(null);
                default -> null;
            };
            if (externalResult != null) {
                return mergeBibliographicInfo(externalResult, fixturePatent);
            }
        }

        return fixturePatent == null ? null : toBibliographicInfo(fixturePatent);
    }

    /**
     * @relatedFR FR-003, FR-004
     * @relatedUI UI-004
     * @description 특허명/제품/기술 키워드를 공식 metadata fixture와 비교해 회사 컨텍스트를 추천한다.
     */
    public PatentContextSuggestionResponse suggestContext(PatentContextSuggestionRequest request) {
        List<String> sourceTokens = tokenizeContextText(String.join(" ",
                valueOrDefault(request.title(), ""),
                valueOrDefault(request.productName(), ""),
                valueOrDefault(request.technologyArea(), ""),
                valueOrDefault(request.businessArea(), ""),
                valueOrDefault(request.applicationNumber(), "")));
        if (sourceTokens.isEmpty()) {
            return null;
        }

        return patents.stream()
                .map(patent -> scoredSuggestion(sourceTokens, patent))
                .max(Comparator.comparingInt(ScoredContextSuggestion::score))
                .filter(candidate -> candidate.score() > 0)
                .map(candidate -> new PatentContextSuggestionResponse(
                        candidate.patent().businessArea(),
                        confidenceText(candidate.score()),
                        "%s 특허의 공식 metadata 키워드와 입력값을 비교했습니다.".formatted(candidate.patent().title()),
                        candidate.patent().technologyArea()))
                .orElse(null);
    }

    /**
     * @relatedFR FR-014, FR-015, FR-016
     * @relatedUI UI-007
     * @description 실제 발송 연동 전, 사업부 검토 요청 메일 발송 상태를 fixture에 반영한다.
     */
    public List<String> markMailingSent(List<String> patentIds) {
        return patentIds.stream()
                .map(patentId -> updatePatent(patentId, patent -> withReviewWorkflowStatus(
                        patent,
                        ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE)))
                .map(PatentDetailResponse::patentId)
                .toList();
    }

    /**
     * @relatedFR FR-003, FR-004
     * @relatedUI UI-004
     * @description 특허 기본 정보와 회사 컨텍스트를 fixture에 등록한다.
     */
    public PatentUpsertResponse createPatent(PatentUpsertRequest request) {
        String patentId = "PAT-2026-%04d".formatted(patentSequence.getAndIncrement());
        patentMetadataRepository.save(metadataEntityFromRequest(patentId, request));
        patents.add(newPatentFromRequest(patentId, request));
        return new PatentUpsertResponse(patentId, "CREATED");
    }

    /**
     * @relatedFR FR-003, FR-004
     * @relatedUI UI-004
     * @description 특허 기본 정보와 회사 컨텍스트를 fixture에서 수정한다.
     */
    public PatentUpsertResponse updatePatent(String patentId, PatentUpsertRequest request) {
        updatePatent(patentId, patent -> withUpsertRequest(patent, request));
        patentMetadataRepository.save(metadataEntityFromRequest(patentId, request));
        return new PatentUpsertResponse(patentId, "UPDATED");
    }

    private PatentDetailResponse findPatent(String patentId) {
        return patents.stream()
                .filter(patent -> patent.patentId().equals(patentId))
                .findFirst()
                .orElseThrow(() -> new PatentFlowException(ErrorCode.PATENT_NOT_FOUND));
    }

    private PatentDetailResponse updatePatent(String patentId, PatentUpdater updater) {
        for (int index = 0; index < patents.size(); index++) {
            PatentDetailResponse patent = patents.get(index);
            if (patent.patentId().equals(patentId)) {
                PatentDetailResponse updated = updater.update(patent);
                patents.set(index, updated);
                return updated;
            }
        }
        throw new PatentFlowException(ErrorCode.PATENT_NOT_FOUND);
    }

    private PatentDetailResponse patent(
            String patentId,
            String managementNumber,
            String applicationNumber,
            String registrationNumber,
            String title,
            String draftTitle,
            String businessArea,
            String technologyArea,
            String productName,
            String departmentId,
            String departmentName,
            ReviewWorkflowStatus reviewWorkflowStatus,
            Recommendation recommendation,
            BusinessOpinionDecision businessOpinionDecision,
            ExecutiveApprovalDecision executiveApprovalDecision,
            LegalActionResult legalActionResult,
            LocalDate annualFeeDueDate) {
        return new PatentDetailResponse(
                patentId,
                managementNumber,
                applicationNumber,
                registrationNumber,
                title,
                draftTitle,
                businessArea,
                technologyArea,
                productName,
                "KR",
                "없음",
                LocalDate.of(2021, 5, 6),
                LocalDate.of(2023, 5, 6),
                LocalDate.of(2041, 5, 6),
                departmentId,
                departmentName,
                PatentLifecycleStatus.ACTIVE,
                reviewWorkflowStatus,
                annualFeeDueDate,
                "연차료 납부 검토 시점 도래",
                recommendation,
                businessOpinionDecision,
                executiveApprovalDecision,
                legalActionResult,
                summary(),
                aiEvaluationReport(recommendation),
                new FinalDecisionRecordResponse(null, executiveApprovalDecision, null, null),
                new BusinessOpinionResponse(businessOpinionDecision, null, null));
    }

    private PatentBibliographicInfoResponse toBibliographicInfo(PatentDetailResponse patent) {
        return new PatentBibliographicInfoResponse(
                patent.managementNumber(),
                valueOrDefault(patent.title(), patent.draftTitle()),
                patent.applicationDate(),
                patent.coApplicants(),
                patent.country(),
                patent.registrationDate(),
                patent.applicationNumber(),
                patent.registrationNumber(),
                patent.expectedExpirationDate(),
                "KIPRIS");
    }

    private PatentBibliographicInfoResponse mergeBibliographicInfo(
            PatentBibliographicInfoResponse externalResult,
            PatentDetailResponse fixturePatent
    ) {
        if (fixturePatent == null) {
            return externalResult;
        }

        return new PatentBibliographicInfoResponse(
                valueOrDefault(fixturePatent.managementNumber(), externalResult.managementNumber()),
                valueOrDefault(externalResult.title(), fixturePatent.title()),
                valueOrDefault(externalResult.applicationDate(), fixturePatent.applicationDate()),
                valueOrDefault(externalResult.coApplicants(), fixturePatent.coApplicants()),
                valueOrDefault(externalResult.country(), fixturePatent.country()),
                valueOrDefault(externalResult.registrationDate(), fixturePatent.registrationDate()),
                valueOrDefault(externalResult.applicationNumber(), fixturePatent.applicationNumber()),
                valueOrDefault(externalResult.registrationNumber(), fixturePatent.registrationNumber()),
                valueOrDefault(externalResult.expectedExpirationDate(), fixturePatent.expectedExpirationDate()),
                externalResult.source());
    }

    private List<String> lookupPriority(String sourcePriority) {
        if (sourcePriority == null || sourcePriority.isBlank()) {
            return List.of("KIPRIS", "GOOGLE_PATENTS");
        }
        return Arrays.stream(sourcePriority.split(","))
                .map(String::trim)
                .filter(source -> !source.isBlank())
                .toList();
    }

    private ScoredContextSuggestion scoredSuggestion(List<String> sourceTokens, PatentDetailResponse patent) {
        Set<String> targetTokens = Set.copyOf(tokenizeContextText(String.join(" ",
                valueOrDefault(patent.title(), ""),
                valueOrDefault(patent.draftTitle(), ""),
                valueOrDefault(patent.productName(), ""),
                valueOrDefault(patent.businessArea(), ""),
                valueOrDefault(patent.technologyArea(), ""))));
        int overlapScore = sourceTokens.stream()
                .mapToInt(token -> targetTokens.contains(token) ? contextTokenWeight(token) : 0)
                .sum();
        int categoryScore = sourceTokens.stream()
                .anyMatch(token -> lowerContains(patent.businessArea(), token)
                        || lowerContains(patent.technologyArea(), token))
                ? 3
                : 0;
        return new ScoredContextSuggestion(patent, overlapScore + categoryScore);
    }

    private List<String> tokenizeContextText(String value) {
        return Arrays.stream(CONTEXT_TOKEN_SPLITTER.split(value.toLowerCase(Locale.ROOT)))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .filter(token -> !CONTEXT_STOP_WORDS.contains(token))
                .distinct()
                .toList();
    }

    private int contextTokenWeight(String token) {
        return token.length() >= 4 ? 2 : 1;
    }

    private String confidenceText(int score) {
        if (score >= 6) {
            return "높음";
        }
        if (score >= 3) {
            return "보통";
        }
        return "낮음";
    }

    private void seedPatentMetadataIfNeeded() {
        if (patentMetadataRepository.count() > 0) {
            return;
        }
        patentMetadataRepository.saveAll(loadPatentMetadataFromDocument());
    }

    private List<PatentDetailResponse> loadPatentsFromDatabase() {
        return patentMetadataRepository.findAll(Sort.by("patentId")).stream()
                .map(this::patentFromMetadataEntity)
                .toList();
    }

    private List<PatentMetadataEntity> loadPatentMetadataFromDocument() {
        try {
            AtomicInteger sequence = new AtomicInteger(1);
            return java.nio.file.Files.readAllLines(java.nio.file.Path.of(PATENT_METADATA_PATH)).stream()
                    .filter(line -> line.startsWith("|"))
                    .filter(line -> !line.contains("---"))
                    .map(this::parseMarkdownRow)
                    .filter(columns -> columns.size() >= 15)
                    .filter(columns -> !columns.get(0).isBlank())
                    .filter(columns -> !"관리번호".equals(columns.get(0)))
                    .map(columns -> metadataEntityFromColumns(sequence.getAndIncrement(), columns))
                    .toList();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("특허 metadata fixture 문서를 읽을 수 없습니다: " + PATENT_METADATA_PATH, exception);
        }
    }

    private List<String> parseMarkdownRow(String line) {
        String normalized = line;
        if (normalized.startsWith("|")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("|")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return Arrays.stream(normalized.split("\\|", -1))
                .map(String::trim)
                .toList();
    }

    private PatentMetadataEntity metadataEntityFromColumns(int sequence, List<String> columns) {
        return new PatentMetadataEntity(
                "PAT-2026-%04d".formatted(sequence),
                columns.get(0),
                columns.get(1),
                columns.get(2),
                columns.get(3),
                columns.get(4),
                columns.get(5),
                columns.get(6),
                columns.get(7),
                columns.get(8),
                columns.get(9),
                parseDate(columns.get(10)),
                parseDate(columns.get(11)),
                columns.get(12),
                columns.get(13),
                parseDate(columns.get(14)));
    }

    private PatentDetailResponse patentFromMetadataEntity(PatentMetadataEntity entity) {
        int sequence = sequenceFromPatentId(entity.getPatentId());
        String managementNumber = entity.getManagementNumber();
        String draftTitle = entity.getDraftTitle();
        String title = entity.getTitle();
        String businessArea = entity.getBusinessArea();
        String technologyArea = entity.getTechnologyArea();
        String productName = entity.getProductName();
        String country = entity.getCountry();
        String jointApplication = entity.getJointApplication();
        String coApplicantName = entity.getCoApplicantName();
        String status = entity.getPatentStatus();
        LocalDate applicationDate = entity.getApplicationDate();
        LocalDate registrationDate = entity.getRegistrationDate();
        String applicationNumber = entity.getApplicationNumber();
        String registrationNumber = entity.getRegistrationNumber();
        LocalDate expectedExpirationDate = entity.getExpectedExpirationDate();
        ReviewWorkflowStatus reviewWorkflowStatus = workflowStatus(sequence);
        Recommendation recommendation = recommendation(sequence);
        BusinessOpinionDecision businessOpinionDecision = businessOpinionDecision(sequence);
        LegalActionResult legalActionResult = legalActionResult(reviewWorkflowStatus, recommendation);

        return new PatentDetailResponse(
                entity.getPatentId(),
                managementNumber,
                applicationNumber,
                registrationNumber,
                title,
                draftTitle,
                businessArea,
                technologyArea,
                productName,
                valueOrDefault(country, "KR"),
                coApplicants(jointApplication, coApplicantName),
                applicationDate,
                registrationDate,
                expectedExpirationDate,
                departmentId(businessArea),
                departmentName(businessArea),
                lifecycleStatus(status),
                reviewWorkflowStatus,
                annualFeeDueDate(applicationDate, expectedExpirationDate),
                "연차료 납부 검토 시점 도래",
                recommendation,
                businessOpinionDecision,
                null,
                legalActionResult,
                summaryFromMetadata(title, technologyArea, productName),
                aiEvaluationReport(recommendation),
                new FinalDecisionRecordResponse(null, null, null, null),
                new BusinessOpinionResponse(businessOpinionDecision, null, null));
    }

    private PatentDetailResponse newPatentFromRequest(String patentId, PatentUpsertRequest request) {
        return new PatentDetailResponse(
                patentId,
                request.managementNumber(),
                request.applicationNumber(),
                request.registrationNumber(),
                request.title(),
                request.title(),
                request.businessArea(),
                request.technologyArea(),
                request.productName(),
                valueOrDefault(request.country(), "KR"),
                valueOrDefault(request.coApplicants(), "없음"),
                request.applicationDate(),
                request.registrationDate(),
                request.expectedExpirationDate(),
                "DEPT-RND",
                "R&D본부",
                PatentLifecycleStatus.ACTIVE,
                ReviewWorkflowStatus.NOT_IN_REVIEW_QUARTER,
                valueOrDefault(request.expectedExpirationDate(), LocalDate.now(ZoneId.of("Asia/Seoul")).plusMonths(3)),
                "작성 필요",
                Recommendation.HOLD,
                null,
                null,
                null,
                new PatentSummaryResponse("작성 필요", "작성 필요", List.of(), "작성 필요", List.of()),
                aiEvaluationReport(Recommendation.HOLD),
                new FinalDecisionRecordResponse(null, null, null, null),
                new BusinessOpinionResponse(null, null, null));
    }

    private PatentMetadataEntity metadataEntityFromRequest(String patentId, PatentUpsertRequest request) {
        return new PatentMetadataEntity(
                patentId,
                request.managementNumber(),
                request.title(),
                request.title(),
                request.businessArea(),
                request.technologyArea(),
                request.productName(),
                valueOrDefault(request.country(), "KR"),
                "N",
                "없음".equals(request.coApplicants()) ? "" : request.coApplicants(),
                "등록",
                request.applicationDate(),
                request.registrationDate(),
                request.applicationNumber(),
                request.registrationNumber(),
                request.expectedExpirationDate());
    }

    private PatentDetailResponse withUpsertRequest(PatentDetailResponse patent, PatentUpsertRequest request) {
        return new PatentDetailResponse(
                patent.patentId(),
                request.managementNumber(),
                request.applicationNumber(),
                request.registrationNumber(),
                request.title(),
                request.title(),
                request.businessArea(),
                request.technologyArea(),
                request.productName(),
                valueOrDefault(request.country(), patent.country()),
                valueOrDefault(request.coApplicants(), patent.coApplicants()),
                request.applicationDate(),
                request.registrationDate(),
                request.expectedExpirationDate(),
                patent.departmentId(),
                patent.departmentName(),
                patent.lifecycleStatus(),
                patent.reviewWorkflowStatus(),
                patent.annualFeeDueDate(),
                patent.reviewReason(),
                patent.currentRecommendation(),
                patent.businessOpinionDecision(),
                patent.executiveApprovalDecision(),
                patent.legalActionResult(),
                patent.summary(),
                patent.aiEvaluationReport(),
                patent.finalDecisionRecord(),
                patent.businessOpinion());
    }

    private PatentDetailResponse withReviewWorkflowStatus(PatentDetailResponse patent, ReviewWorkflowStatus status) {
        return new PatentDetailResponse(
                patent.patentId(),
                patent.managementNumber(),
                patent.applicationNumber(),
                patent.registrationNumber(),
                patent.title(),
                patent.draftTitle(),
                patent.businessArea(),
                patent.technologyArea(),
                patent.productName(),
                patent.country(),
                patent.coApplicants(),
                patent.applicationDate(),
                patent.registrationDate(),
                patent.expectedExpirationDate(),
                patent.departmentId(),
                patent.departmentName(),
                patent.lifecycleStatus(),
                status,
                patent.annualFeeDueDate(),
                patent.reviewReason(),
                patent.currentRecommendation(),
                patent.businessOpinionDecision(),
                patent.executiveApprovalDecision(),
                patent.legalActionResult(),
                patent.summary(),
                patent.aiEvaluationReport(),
                patent.finalDecisionRecord(),
                patent.businessOpinion());
    }

    private PatentDetailResponse withBusinessOpinion(
            PatentDetailResponse patent,
            BusinessOpinionDecision decision,
            String reason,
            OffsetDateTime submittedAt
    ) {
        return new PatentDetailResponse(
                patent.patentId(),
                patent.managementNumber(),
                patent.applicationNumber(),
                patent.registrationNumber(),
                patent.title(),
                patent.draftTitle(),
                patent.businessArea(),
                patent.technologyArea(),
                patent.productName(),
                patent.country(),
                patent.coApplicants(),
                patent.applicationDate(),
                patent.registrationDate(),
                patent.expectedExpirationDate(),
                patent.departmentId(),
                patent.departmentName(),
                patent.lifecycleStatus(),
                ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED,
                patent.annualFeeDueDate(),
                patent.reviewReason(),
                patent.currentRecommendation(),
                decision,
                patent.executiveApprovalDecision(),
                patent.legalActionResult(),
                patent.summary(),
                patent.aiEvaluationReport(),
                patent.finalDecisionRecord(),
                new BusinessOpinionResponse(decision, reason, submittedAt));
    }

    private PatentDetailResponse withExecutiveApproval(
            PatentDetailResponse patent,
            ExecutiveApprovalDecision decision,
            OffsetDateTime decidedAt
    ) {
        return new PatentDetailResponse(
                patent.patentId(),
                patent.managementNumber(),
                patent.applicationNumber(),
                patent.registrationNumber(),
                patent.title(),
                patent.draftTitle(),
                patent.businessArea(),
                patent.technologyArea(),
                patent.productName(),
                patent.country(),
                patent.coApplicants(),
                patent.applicationDate(),
                patent.registrationDate(),
                patent.expectedExpirationDate(),
                patent.departmentId(),
                patent.departmentName(),
                patent.lifecycleStatus(),
                ReviewWorkflowStatus.APPROVAL_COMPLETED,
                patent.annualFeeDueDate(),
                patent.reviewReason(),
                patent.currentRecommendation(),
                patent.businessOpinionDecision(),
                decision,
                legalActionResult(decision),
                patent.summary(),
                patent.aiEvaluationReport(),
                new FinalDecisionRecordResponse(
                        patent.patentId() + "-DEC-01",
                        decision,
                        null,
                        decidedAt),
                patent.businessOpinion());
    }

    private LegalActionResult legalActionResult(ExecutiveApprovalDecision decision) {
        return switch (decision) {
            case APPROVED_ABANDON -> LegalActionResult.ABANDONED;
            case APPROVED_SELL -> LegalActionResult.SOLD;
            case APPROVED_MAINTAIN, REJECTED, REQUEST_CHANGES -> null;
        };
    }

    private PatentSummaryResponse summary() {
        return new PatentSummaryResponse(
                "특허 내용을 비전문가도 이해할 수 있도록 요약한 테스트 데이터입니다.",
                "기존 업무 검토 과정의 반복 작업을 줄이는 문제를 다룹니다.",
                List.of("문서 요약", "근거 추출", "평가 기준 매핑"),
                "청구항의 주요 권리 범위를 요약한 테스트 데이터입니다.",
                List.of("시장 규모 자료", "실제 제품 적용 여부"));
    }

    private PatentSummaryResponse summaryFromMetadata(String title, String technologyArea, String productName) {
        return new PatentSummaryResponse(
                "%s 관련 특허의 공식 metadata 기반 mock 요약입니다.".formatted(title),
                "%s 영역에서 발생하는 업무 또는 기술 문제를 해결하기 위한 특허입니다.".formatted(valueOrDefault(technologyArea, "관련 기술")),
                List.of(valueOrDefault(technologyArea, "추가 확인 필요"), valueOrDefault(productName, "관련 제품 정보 부족")),
                "청구항 상세 분석 전 단계의 mock 권리 범위 요약입니다.",
                List.of("상세 청구항 분석 자료", "시장 규모 자료", "제품 적용 여부"));
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private String coApplicants(String jointApplication, String coApplicantName) {
        if ("Y".equalsIgnoreCase(jointApplication) && !coApplicantName.isBlank()) {
            return coApplicantName;
        }
        if ("Y".equalsIgnoreCase(jointApplication)) {
            return "정보 부족 있음";
        }
        return "없음";
    }

    private PatentLifecycleStatus lifecycleStatus(String status) {
        if ("등록".equals(status)) {
            return PatentLifecycleStatus.ACTIVE;
        }
        if ("소멸".equals(status)) {
            return PatentLifecycleStatus.EXPIRED;
        }
        if ("포기".equals(status)) {
            return PatentLifecycleStatus.ABANDONED;
        }
        return PatentLifecycleStatus.ACTIVE;
    }

    private ReviewWorkflowStatus workflowStatus(int sequence) {
        ReviewWorkflowStatus[] statuses = {
                ReviewWorkflowStatus.REVIEW_QUARTER_STARTED,
                ReviewWorkflowStatus.REPORT_GENERATED,
                ReviewWorkflowStatus.MAIL_READY,
                ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE,
                ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED,
                ReviewWorkflowStatus.LEGAL_ACTION_RECORDED
        };
        return statuses[(sequence - 1) % statuses.length];
    }

    private Recommendation recommendation(int sequence) {
        Recommendation[] recommendations = {
                Recommendation.MAINTAIN,
                Recommendation.REVIEW_AGAIN,
                Recommendation.ABANDON
        };
        return recommendations[(sequence - 1) % recommendations.length];
    }

    private BusinessOpinionDecision businessOpinionDecision(int sequence) {
        if (sequence % 6 == 0) {
            return BusinessOpinionDecision.ABANDON;
        }
        if (sequence % 4 == 0) {
            return BusinessOpinionDecision.MAINTAIN;
        }
        return null;
    }

    private LegalActionResult legalActionResult(ReviewWorkflowStatus status, Recommendation recommendation) {
        if (status != ReviewWorkflowStatus.LEGAL_ACTION_RECORDED) {
            return null;
        }
        if (recommendation == Recommendation.ABANDON) {
            return LegalActionResult.ABANDONED;
        }
        return LegalActionResult.MAINTAINED;
    }

    private LocalDate annualFeeDueDate(LocalDate applicationDate, LocalDate expectedExpirationDate) {
        LocalDate baseDate = applicationDate != null ? applicationDate : expectedExpirationDate;
        if (baseDate == null) {
            return LocalDate.of(2026, 12, 31);
        }
        return LocalDate.of(2026, baseDate.getMonth(), baseDate.getDayOfMonth());
    }

    private int sequenceFromPatentId(String patentId) {
        if (patentId == null || patentId.length() < 4) {
            return 1;
        }
        try {
            return Integer.parseInt(patentId.substring(patentId.length() - 4));
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private String departmentId(String businessArea) {
        return switch (valueOrDefault(businessArea, "")) {
            case "AI", "Data" -> "DEPT-RND";
            case "Blockchain" -> "DEPT-PLATFORM";
            case "ESG" -> "DEPT-ESG";
            case "통신" -> "DEPT-ICT";
            case "제조" -> "DEPT-MFG";
            default -> "DEPT-BIZ";
        };
    }

    private String departmentName(String businessArea) {
        return switch (valueOrDefault(businessArea, "")) {
            case "AI", "Data" -> "R&D본부";
            case "Blockchain" -> "플랫폼사업부";
            case "ESG" -> "ESG사업부";
            case "통신" -> "ICT사업부";
            case "제조" -> "제조사업부";
            default -> "사업기획팀";
        };
    }

    private AiEvaluationReportResponse aiEvaluationReport(Recommendation recommendation) {
        return new AiEvaluationReportResponse(
                "EVAL-2026-0001",
                OffsetDateTime.of(2026, 5, 6, 10, 0, 0, 0,
                        ZoneId.of("Asia/Seoul").getRules().getOffset(java.time.Instant.now())),
                recommendation,
                "추가 자료 확인 후 유지 여부를 재검토하는 것이 적절합니다.",
                72,
                List.of(
                        new EvaluationScoreResponse(EvaluationCategory.RIGHTS, 70,
                                "청구항 범위는 확인되나 일부 권리 범위 비교 자료가 부족합니다."),
                        new EvaluationScoreResponse(EvaluationCategory.TECHNOLOGY, 78, "명세서상 기술적 차별 요소가 확인됩니다."),
                        new EvaluationScoreResponse(EvaluationCategory.MARKET, null, "시장 규모 자료가 부족하여 추가 확인이 필요합니다."),
                        new EvaluationScoreResponse(EvaluationCategory.LIFECYCLE_ECONOMICS, 68,
                                "유지 비용 대비 활용 가능성 검토가 필요합니다.")),
                List.of("시장 규모 자료", "제품 적용 여부"));
    }

    private PatentListItemResponse toListItem(PatentDetailResponse detail) {
        return new PatentListItemResponse(
                detail.patentId(),
                detail.managementNumber(),
                detail.applicationNumber(),
                detail.registrationNumber(),
                detail.title(),
                detail.draftTitle(),
                detail.businessArea(),
                detail.technologyArea(),
                detail.productName(),
                detail.country(),
                detail.coApplicants(),
                detail.applicationDate(),
                detail.registrationDate(),
                detail.expectedExpirationDate(),
                detail.departmentId(),
                detail.departmentName(),
                detail.lifecycleStatus(),
                detail.reviewWorkflowStatus(),
                detail.annualFeeDueDate(),
                detail.reviewReason(),
                detail.currentRecommendation(),
                detail.businessOpinionDecision(),
                detail.executiveApprovalDecision(),
                detail.legalActionResult());
    }

    private boolean matchesKeyword(PatentListItemResponse item, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        return List.of(item.title(), item.managementNumber(), item.applicationNumber(), item.registrationNumber())
                .stream()
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(lowerKeyword));
    }

    private Comparator<PatentListItemResponse> sortComparator(String sort) {
        Comparator<PatentListItemResponse> comparator = Comparator.comparing(
                PatentListItemResponse::annualFeeDueDate,
                Comparator.nullsLast(Comparator.naturalOrder()));
        if (sort == null || sort.isBlank()) {
            return comparator;
        }
        String[] parts = sort.split(",");
        if ("title".equals(parts[0])) {
            comparator = Comparator.comparing(PatentListItemResponse::title);
        } else if ("managementNumber".equals(parts[0])) {
            comparator = Comparator.comparing(PatentListItemResponse::managementNumber);
        }
        if (parts.length > 1 && "desc".equalsIgnoreCase(parts[1])) {
            return comparator.reversed();
        }
        return comparator;
    }

    private String valueOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private LocalDate valueOrDefault(LocalDate value, LocalDate defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    private boolean lowerEquals(String value, String lowerKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).equals(lowerKeyword);
    }

    private boolean lowerContains(String value, String token) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(token);
    }

    private interface PatentUpdater {
        PatentDetailResponse update(PatentDetailResponse patent);
    }

    private record ScoredContextSuggestion(PatentDetailResponse patent, int score) {
    }
}
