package com.syuuk.patentflow.patent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.response.PageInfo;
import com.syuuk.patentflow.common.response.PageResponse;
import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import com.syuuk.patentflow.patent.domain.PatentReviewHistoryEntity;
import com.syuuk.patentflow.patent.dto.AiEvaluationReportResponse;
import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import com.syuuk.patentflow.patent.dto.BusinessOpinionResponse;
import com.syuuk.patentflow.patent.dto.EvaluationCategory;
import com.syuuk.patentflow.patent.dto.EvaluationScoreResponse;
import com.syuuk.patentflow.patent.dto.FinalDecisionRequest;
import com.syuuk.patentflow.patent.dto.FinalDecisionResponse;
import com.syuuk.patentflow.patent.dto.FinalDecisionRecordResponse;
import com.syuuk.patentflow.patent.dto.LegalActionResult;
import com.syuuk.patentflow.patent.dto.PatchFinalDecisionRequest;
import com.syuuk.patentflow.patent.dto.PatentBibliographicInfoResponse;
import com.syuuk.patentflow.patent.dto.PatentContextSuggestionRequest;
import com.syuuk.patentflow.patent.dto.PatentContextSuggestionResponse;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import com.syuuk.patentflow.patent.dto.PatentHistoryResponse;
import com.syuuk.patentflow.patent.dto.PatentLifecycleStatus;
import com.syuuk.patentflow.patent.dto.PatentListItemResponse;
import com.syuuk.patentflow.patent.dto.PatentReviewHistoryItemResponse;
import com.syuuk.patentflow.patent.dto.PatentSummaryResponse;
import com.syuuk.patentflow.patent.dto.PatentUpsertRequest;
import com.syuuk.patentflow.patent.dto.PatentUpsertResponse;
import com.syuuk.patentflow.patent.dto.Recommendation;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.mailing.domain.DepartmentEntity;
import com.syuuk.patentflow.mailing.repository.DepartmentRepository;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class PatentReviewService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String PATENT_METADATA_PATH = "docs/skax_patents_list.md";

    private final PatentMetadataRepository patentMetadataRepository;
    private final PatentReviewHistoryRepository reviewHistoryRepository;
    private final AnnualFeeScheduleService annualFeeScheduleService;
    private final DepartmentRepository mailingRecipientMappingRepository;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final PatentWorkflowService workflowService;
    private final PatentLookupService lookupService;
    private Map<String, String> departmentNameCache;

    public record WorkflowBatchUpdateResult(
            List<String> updatedPatentIds,
            List<String> skippedPatentIds
    ) {}

    public PatentReviewService(
            PatentMetadataRepository patentMetadataRepository,
            PatentReviewHistoryRepository reviewHistoryRepository,
            AnnualFeeScheduleService annualFeeScheduleService,
            DepartmentRepository mailingRecipientMappingRepository,
            ObjectMapper objectMapper,
            Environment environment,
            PatentWorkflowService workflowService,
            PatentLookupService lookupService
    ) {
        this.patentMetadataRepository = patentMetadataRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.annualFeeScheduleService = annualFeeScheduleService;
        this.mailingRecipientMappingRepository = mailingRecipientMappingRepository;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.workflowService = workflowService;
        this.lookupService = lookupService;
        if (!usesSqlSeedRunner()) {
            seedDepartmentsIfNeeded();
        }
        this.departmentNameCache = mailingRecipientMappingRepository.findAll().stream()
                .collect(Collectors.toMap(
                        DepartmentEntity::getDepartmentId,
                        DepartmentEntity::getDepartmentName,
                        (a, b) -> a,
                        HashMap::new));
        if (!usesSqlSeedRunner()) {
            seedPatentMetadataIfNeeded();
            seedReviewHistoryIfNeeded();
        }
    }

        /**
        * @relatedFR FR-LEGAL-01, FR-LEGAL-02
     * @relatedUI UI-COM-02, UI-LEGAL-02
     * @description DB 기반 페이징 및 필터링을 제공한다.
     */
    public PageResponse<PatentListItemResponse> getPatents(
            int page,
            int size,
            String keyword,
            String departmentId,
            ReviewWorkflowStatus reviewWorkflowStatus,
            String sort) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);

        List<PatentListItemResponse> filtered = getAllPatents().stream()
                .filter(item -> departmentId == null || departmentId.isBlank()
                     
                || departmentId.equals(item.departmentId()))
                .filter(item -> reviewWorkflowStatus == null
                        || item.reviewWorkflowStatus() == reviewWorkflowStatus)
                .filter(item -> matchesKeyword(item, keyword))
                .sorted(sortComparator(sort))
                .toList();

        int totalItems = filtered.size();
        int fromIndex = Math.min((normalizedPage - 1) * normalizedSize, totalItems);
        int toIndex = Math.min(fromIndex + normalizedSize, totalItems);
        List<PatentListItemResponse> items = filtered.subList(fromIndex, toIndex);
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / normalizedSize);

        return PageResponse.ok(
                items,
                new PageInfo(normalizedPage, normalizedSize, totalItems, totalPages));
    }

    public List<PatentListItemResponse> getReviewTargets(
            String quarter,
            String country,
            LocalDate dateFrom,
            LocalDate dateTo,
            ReviewWorkflowStatus reviewWorkflowStatus) {
        String normalizedQuarter = quarter == null ? null : quarter.trim().toUpperCase(Locale.ROOT);
        String normalizedCountry = country == null ? null : country.trim().toUpperCase(Locale.ROOT);

        return getAllPatents().stream()
                .filter(patent -> reviewWorkflowStatus == null || patent.reviewWorkflowStatus() == reviewWorkflowStatus)
                .filter(patent -> normalizedCountry == null || normalizedCountry.isBlank()
                        || "ALL".equals(normalizedCountry)
                        || normalizedCountry.equalsIgnoreCase(patent.country()))
                .filter(patent -> matchesDateRange(patent.feeDueDate(), dateFrom, dateTo))
                .filter(patent -> normalizedQuarter == null || normalizedQuarter.isBlank()
                        || "ALL".equals(normalizedQuarter)
                        || normalizedQuarter.equals(quarterKey(patent.feeDueDate())))
                .toList();
    }

    private boolean matchesDateRange(LocalDate date, LocalDate dateFrom, LocalDate dateTo) {
        if (date == null) {
            return dateFrom == null && dateTo == null;
        }
        return (dateFrom == null || !date.isBefore(dateFrom))
                && (dateTo == null || !date.isAfter(dateTo));
    }

    private String quarterKey(LocalDate date) {
        if (date == null) {
            return null;
        }
        return "Q" + ((date.getMonthValue() - 1) / 3 + 1);
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "annualFeeDueDate");
        }
        String[] parts = sort.split(",");
        String property = parts[0];
        if ("managementNumber".equals(property)) {
            // managementNumber is in metadata, sorting history by patentId as proxy if relevant
            property = "patentId";
        } else if ("feeDueDate".equals(property)) {
            property = "annualFeeDueDate";
        }

        Sort.Direction direction = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1]))
                ? Sort.Direction.DESC : Sort.Direction.ASC;

        return Sort.by(direction, property);
    }

    private PatentListItemResponse toListItemFromHistory(PatentReviewHistoryEntity history, PatentMetadataEntity metadata) {
        if (metadata == null) {
            // Fallback for missing metadata
            return new PatentListItemResponse(
                    history.getPatentId(), history.getPatentId(), null, null,
                    "Unknown", "Unknown", null, null, null, "KR", null,
                    null, null, null, history.getDepartmentId(), history.getDepartmentName(),
                    PatentLifecycleStatus.ACTIVE, history.getReviewWorkflowStatus(),
                    history.getAnnualFeeDueDate(), null, history.getAiRecommendation(),
                    history.getBusinessOpinionDecision(), history.getLegalActionResult(),
                    null,
                    history.getReviewWorkflowStatus() != ReviewWorkflowStatus.NOT_IN_REVIEW,
                    null
            );
        }
        return new PatentListItemResponse(
                metadata.getPatentId(),
                metadata.getManagementNumber(),
                metadata.getApplicationNumber(),
                metadata.getRegistrationNumber(),
                metadata.getTitle(),
                metadata.getDraftTitle(),
                metadata.getBusinessArea(),
                metadata.getTechnologyArea(),
                metadata.getProductName(),
                metadata.getCountry(),
                coApplicants(metadata.getJointApplication(), metadata.getCoApplicantName()),
                metadata.getApplicationDate(),
                metadata.getRegistrationDate(),
                metadata.getExpectedExpirationDate(),
                history.getDepartmentId(),
                history.getDepartmentName(),
                metadata.getPatentStatus(),
                history.getReviewWorkflowStatus(),
                history.getAnnualFeeDueDate(),
                "연차료 납부 검토 시점 도래",
                history.getAiRecommendation(),
                history.getBusinessOpinionDecision(),
                history.getLegalActionResult(),
                originalPatentUrl(metadata.getCountry(), metadata.getApplicationNumber(), metadata.getRegistrationNumber()),
                metadata.isInReview(),
                metadata.getCurrentQuarterKey()
        );
    }

    public PageResponse<PatentListItemResponse> getReviewRequests(
            int page,
            int size,
            String departmentId
    ) {
        return getPatents(page, size, null, departmentId, ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE, null);
    }

    /**
     * @relatedFR FR-LEGAL-05, FR-LEGAL-06, FR-LEGAL-07, FR-LEGAL-08, FR-LEGAL-09, FR-LEGAL-10
     * @relatedUI UI-LEGAL-04
     * @description AI 특허 평가 레포트와 최종 판단을 분리해 특허 상세를 조회한다.
     */
    public PatentDetailResponse getPatentDetail(String patentId) {
        return findPatent(patentId);
    }

    public void ensurePatentExists(String patentId) {
        findPatent(patentId);
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

    public PatentDetailResponse generateAiReport(String patentId) {
        return workflowService.generateAiReport(patentId);
    }

    // 배치 자동 생성 전용 — PatentWorkflowService.generateAiReportForBatch 참조
    public PatentDetailResponse generateAiReportForBatch(String patentId) {
        return workflowService.generateAiReportForBatch(patentId);
    }

    public List<String> markMailReady(List<String> patentIds) {
        return workflowService.markMailReady(patentIds);
    }

    public void recordBusinessOpinion(
            String patentId,
            BusinessOpinionDecision decision,
            String reason,
            OffsetDateTime submittedAt
    ) {
        workflowService.recordBusinessOpinion(patentId, decision, reason, submittedAt);
    }

    /**
     * @relatedFR FR-LEGAL-11
     * @relatedUI UI-LEGAL-04, UI-BUS-05
     * @description 평가/판단 이력을 조회한다.
     */
    public List<PatentHistoryResponse> getPatentHistory(String patentId) {
        getPatentDetail(patentId);
        return reviewHistoryRepository.findByPatentIdOrderByCreatedAtDesc(patentId).stream()
                .flatMap(history -> patentHistoryResponses(history).stream())
                .sorted(Comparator.comparing(PatentHistoryResponse::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private List<PatentHistoryResponse> patentHistoryResponses(PatentReviewHistoryEntity history) {
        List<PatentHistoryResponse> responses = new ArrayList<>();
        OffsetDateTime createdAt = history.getCreatedAt() == null
                ? null
                : history.getCreatedAt().atZone(KST).toOffsetDateTime();

        responses.add(new PatentHistoryResponse(
                history.getId() + "-WORKFLOW",
                "WORKFLOW_STATUS_UPDATED",
                "검토 상태 기록",
                "%s 분기 상태가 %s로 기록되었습니다.".formatted(
                        history.getQuarterKey(),
                        history.getReviewWorkflowStatus() == null ? "미정" : history.getReviewWorkflowStatus().name()),
                "PatentFlow",
                createdAt));

        if (history.getAiReportCreatedAt() != null) {
            responses.add(new PatentHistoryResponse(
                    history.getId() + "-AI",
                    "AI_EVALUATION_CREATED",
                    "AI 평가 레포트 생성",
                    "%s 분기 AI 특허 평가 레포트가 생성되었습니다.".formatted(history.getQuarterKey()),
                    "AI Evaluation Service",
                    history.getAiReportCreatedAt()));
        }

        if (history.getBusinessOpinionDecision() != null || history.getBusinessOpinionSubmittedAt() != null) {
            responses.add(new PatentHistoryResponse(
                    history.getId() + "-BUSINESS",
                    "BUSINESS_OPINION_SUBMITTED",
                    "사업부 의견 제출",
                    "사업부가 %s 의견을 제출했습니다.".formatted(
                            history.getBusinessOpinionDecision() == null ? "검토" : history.getBusinessOpinionDecision().name()),
                    valueOrDefault(history.getDepartmentName(), "사업부"),
                    history.getBusinessOpinionSubmittedAt() != null ? history.getBusinessOpinionSubmittedAt() : createdAt));
        }

        if (history.getLegalActionResult() != null || history.getFinalDecisionDecidedAt() != null) {
            responses.add(new PatentHistoryResponse(
                    history.getId() + "-FINAL",
                    "FINAL_DECISION_RECORDED",
                    "최종 판단 기록",
                    "관리자가 %s 결과를 기록했습니다.".formatted(
                            history.getLegalActionResult() == null ? "최종 판단" : history.getLegalActionResult().name()),
                    "관리자",
                    history.getFinalDecisionDecidedAt() != null ? history.getFinalDecisionDecidedAt() : createdAt));
        }

        return responses;
    }

    /**
     * @relatedFR FR-LEGAL-03
     * @relatedUI UI-LEGAL-02, UI-LEGAL-03
     * @description 공식 metadata에서 관리번호/출원번호/등록번호 기반 외부 검색 결과를 제공한다.
     */
    public PatentBibliographicInfoResponse lookupBibliographicInfo(
            String managementNumber,
            String applicationNumber,
            String registrationNumber,
            String sourcePriority
    ) {
        // allPatents를 여기서 로드해 PatentLookupService에 전달 — 순환 의존성 없이 DB 접근 분리
        return lookupService.lookupBibliographicInfo(
                managementNumber, applicationNumber, registrationNumber, sourcePriority, loadPatentsFromDatabase());
    }

    public PatentContextSuggestionResponse suggestContext(PatentContextSuggestionRequest request) {
        return lookupService.suggestContext(request, loadPatentsFromDatabase());
    }

    /**
     * @relatedFR FR-LEGAL-12, FR-LEGAL-13, FR-LEGAL-14
     * @relatedUI UI-LEGAL-05
     * @description 실제 발송 연동 전, 사업부 검토 요청 메일 발송 상태를 검토 상태에 반영한다.
     */
    public WorkflowBatchUpdateResult markMailingSent(List<String> patentIds) {
        return workflowService.markMailingSent(patentIds);
    }

    /**
     * @relatedFR FR-LEGAL-03, FR-LEGAL-04
     * @relatedUI UI-LEGAL-02, UI-LEGAL-03
     * @description 특허 기본 정보와 회사 컨텍스트를 DB에 등록한다.
     */
    public PatentUpsertResponse createPatent(PatentUpsertRequest request) {
        String patentId = nextPatentId();
        patentMetadataRepository.save(metadataEntityFromRequest(patentId, request));
        PatentDetailResponse created = newPatentFromRequest(patentId, request);
        persistPatentState(created);
        return new PatentUpsertResponse(patentId, "CREATED");
    }

    /**
     * @relatedFR FR-LEGAL-03, FR-LEGAL-04
     * @relatedUI UI-LEGAL-02, UI-LEGAL-03
     * @description 특허 기본 정보와 회사 컨텍스트를 DB에서 수정한다.
     */
    public PatentUpsertResponse updatePatent(String patentId, PatentUpsertRequest request) {
        updatePatent(patentId, patent -> withUpsertRequest(patent, request));
        patentMetadataRepository.save(metadataEntityFromRequest(patentId, request));
        return new PatentUpsertResponse(patentId, "UPDATED");
    }

    private String nextPatentId() {
        int nextSequence = patentMetadataRepository.findMaxPatentSequence() + 1;
        return "PAT-2026-%04d".formatted(nextSequence);
    }

    public PatentDetailResponse assignDepartment(String patentId, String departmentId) {
        String departmentName = resolvedDepartmentName(departmentId);
        PatentDetailResponse updated = updatePatent(patentId, patent -> withDepartment(patent, departmentId, departmentName));
        persistPatentState(updated);
        return updated;
    }

    public List<PatentListItemResponse> getAllPatents() {
        return patentMetadataRepository.findAll(Sort.by("patentId")).stream()
                .map(entity -> toListItem(patentFromMetadataEntity(entity), entity.getCurrentQuarterKey()))
                .toList();
    }

    public List<String> createQuarterReviewTargets(
            String quarterKey, LocalDate paymentPeriodStart, LocalDate paymentPeriodEnd
    ) {
        return workflowService.createQuarterReviewTargets(quarterKey, paymentPeriodStart, paymentPeriodEnd);
    }

    public void bulkUpdateWorkflowStatus(List<String> patentIds, ReviewWorkflowStatus newStatus, String quarterKey) {
        workflowService.bulkUpdateWorkflowStatus(patentIds, newStatus, quarterKey);
    }

    public List<PatentReviewHistoryItemResponse> getReviewHistory(String patentId) {
        ensurePatentExists(patentId);
        return reviewHistoryRepository.findByPatentIdOrderByCreatedAtDesc(patentId).stream()
                .map(this::toHistoryItem)
                .toList();
    }


    public FinalDecisionResponse patchFinalDecision(String patentId, PatchFinalDecisionRequest request) {
        return workflowService.patchFinalDecision(patentId, request);
    }

    public FinalDecisionResponse recordFinalDecision(String patentId, FinalDecisionRequest request) {
        return workflowService.recordFinalDecision(patentId, request);
    }

    PatentDetailResponse findPatent(String patentId) {
        return loadPatentsFromDatabase().stream()
                .filter(patent -> patent.patentId().equals(patentId))
                .findFirst()
                .orElseThrow(() -> new PatentFlowException(ErrorCode.PATENT_NOT_FOUND));
    }

    PatentDetailResponse findPatentOrNull(String patentId) {
        return loadPatentsFromDatabase().stream()
                .filter(patent -> patent.patentId().equals(patentId))
                .findFirst()
                .orElse(null);
    }

    private PatentDetailResponse updatePatent(String patentId, PatentUpdater updater) {
        return updatePatentInternal(patentId, updater::update);
    }

    // PatentWorkflowService에서 접근 — 같은 패키지 내 공유 메서드
    PatentDetailResponse updatePatentInternal(String patentId, java.util.function.Function<PatentDetailResponse, PatentDetailResponse> fn) {
        PatentDetailResponse patent = findPatent(patentId);
        PatentDetailResponse updated = fn.apply(patent);
        persistPatentState(updated);
        return updated;
    }

    private void seedDepartmentsIfNeeded() {
        if (mailingRecipientMappingRepository.count() > 0) {
            return;
        }
        List<DepartmentEntity> defaults = List.of(
                new DepartmentEntity("DEPT-RND",      "R&D본부",    LocalDate.now()),
                new DepartmentEntity("DEPT-PLATFORM", "플랫폼사업부", LocalDate.now()),
                new DepartmentEntity("DEPT-ESG",      "ESG사업부",  LocalDate.now()),
                new DepartmentEntity("DEPT-ICT",      "ICT사업부",  LocalDate.now()),
                new DepartmentEntity("DEPT-MFG",      "제조사업부", LocalDate.now()),
                new DepartmentEntity("DEPT-BIZ",      "사업기획팀", LocalDate.now())
        );
        mailingRecipientMappingRepository.saveAll(defaults);
    }

    private void seedPatentMetadataIfNeeded() {
        if (patentMetadataRepository.count() > 0) {
            return;
        }
        patentMetadataRepository.saveAll(loadPatentMetadataFromDocument());
    }

    private void seedReviewHistoryIfNeeded() {
        patentMetadataRepository.findAll(Sort.by("patentId")).forEach(entity -> {
            if (!reviewHistoryRepository.findByPatentIdOrderByCreatedAtDesc(entity.getPatentId()).isEmpty()) {
                return;
            }
            reviewHistoryRepository.save(reviewHistoryFromMetadataEntity(entity));
        });
    }

    private boolean usesSqlSeedRunner() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "local".equals(profile) || "demo".equals(profile));
    }

    private List<PatentDetailResponse> loadPatentsFromDatabase() {
        return patentMetadataRepository.findAll(Sort.by("patentId")).stream()
                .map(this::patentFromMetadataEntity)
                .toList();
    }

    private List<PatentMetadataEntity> loadPatentMetadataFromDocument() {
        try {
            AtomicInteger sequence = new AtomicInteger(1);
            java.util.List<String> lines;

            // Prefer classpath resource (works when running from a fat jar). Fall back to filesystem.
            try (java.io.InputStream is = this.getClass().getClassLoader().getResourceAsStream(PATENT_METADATA_PATH)) {
                if (is != null) {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                        lines = reader.lines().toList();
                    }
                } else {
                    lines = java.nio.file.Files.readAllLines(java.nio.file.Path.of(PATENT_METADATA_PATH));
                }
            }

            return lines.stream()
                    .filter(line -> line.startsWith("|"))
                    .filter(line -> !line.contains("---"))
                    .map(this::parseMarkdownRow)
                    .filter(columns -> columns.size() >= 15)
                    .filter(columns -> !columns.get(0).isBlank())
                    .filter(columns -> !"관리번호".equals(columns.get(0)))
                    .map(columns -> metadataEntityFromColumns(sequence.getAndIncrement(), columns))
                    .toList();
        } catch (java.io.IOException exception) {
            throw new PatentFlowException(ErrorCode.INTERNAL_ERROR, "특허 metadata 문서를 읽을 수 없습니다: " + PATENT_METADATA_PATH);
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
        String businessArea = columns.get(3);
        return new PatentMetadataEntity(
                "PAT-2026-%04d".formatted(sequence),
                columns.get(0),
                columns.get(1),
                columns.get(2),
                businessArea,
                columns.get(4),
                columns.get(5),
                columns.get(6),
                columns.get(7),
                columns.get(8),
                lifecycleStatus(columns.get(9)),
                parseDate(columns.get(10)),
                parseDate(columns.get(11)),
                columns.get(12),
                columns.get(13),
                parseDate(columns.get(14)),
                null);
    }

    private PatentReviewHistoryEntity reviewHistoryFromMetadataEntity(PatentMetadataEntity entity) {
        int sequence = sequenceFromPatentId(entity.getPatentId());
        ReviewWorkflowStatus reviewWorkflowStatus = workflowStatus(sequence);
        Recommendation recommendation = recommendation(sequence);
        BusinessOpinionDecision businessOpinionDecision = businessOpinionDecision(sequence, reviewWorkflowStatus);
        LegalActionResult legalActionResult = legalActionResult(reviewWorkflowStatus, recommendation);
        OffsetDateTime businessOpinionSubmittedAt = businessOpinionDecision == null
                ? null
                : OffsetDateTime.of(
                        2026,
                        5,
                        Math.min(28, 1 + sequence % 20),
                        14,
                        20,
                        0,
                        0,
                        KST.getRules().getOffset(java.time.Instant.now()));
        LocalDate annualFeeDueDate = entity.getFeeDueDate() != null
                ? entity.getFeeDueDate()
                : annualFeeScheduleService.calculateNextDueDate(
                        entity.getCountry(),
                        entity.getApplicationDate(),
                        entity.getRegistrationDate(),
                        entity.getExpectedExpirationDate());
        PatentReviewHistoryEntity history = new PatentReviewHistoryEntity(entity.getPatentId(), "DEMO-SEED");
        history.setReviewWorkflowStatus(reviewWorkflowStatus);
        history.setAiRecommendation(recommendation);
        applyAiReportToHistory(history, aiEvaluationReport(recommendation));
        applySummaryToHistory(history, summaryFromMetadata(
                valueOrDefault(entity.getTitle(), entity.getDraftTitle()),
                entity.getTechnologyArea(),
                entity.getProductName()));
        history.setBusinessOpinionDecision(businessOpinionDecision);
        history.setBusinessOpinionReason(businessOpinionDecision == null ? null : defaultBusinessOpinionReason(businessOpinionDecision));
        history.setBusinessOpinionSubmittedAt(businessOpinionSubmittedAt);
        history.setLegalActionResult(legalActionResult);
        history.setFinalDecisionId(legalActionResult == null ? null : entity.getPatentId() + "-DEC-01");
        history.setFinalDecisionReason(legalActionResult == null ? null : defaultFinalDecisionReason(legalActionResult));
        history.setFinalDecisionDecidedAt(legalActionResult == null ? null : businessOpinionSubmittedAt);
        history.setAnnualFeeDueDate(annualFeeDueDate);
        history.setDepartmentId(departmentId(entity.getBusinessArea()));
        history.setDepartmentName(departmentName(entity.getBusinessArea()));
        return history;
    }

    private PatentDetailResponse patentFromMetadataEntity(PatentMetadataEntity entity) {
        PatentLifecycleStatus ls = entity.getPatentStatus();
        LocalDate expectedExpirationDate = entity.getExpectedExpirationDate();
        LocalDate baseFeeDate = entity.getFeeDueDate() != null
                ? entity.getFeeDueDate()
                : annualFeeScheduleService.calculateNextDueDate(
                        entity.getCountry(), entity.getApplicationDate(),
                        entity.getRegistrationDate(), expectedExpirationDate);

        if (ls == PatentLifecycleStatus.ACTIVE && baseFeeDate != null
                && baseFeeDate.isBefore(LocalDate.now(KST))) {
            ls = PatentLifecycleStatus.ABANDONED;
            entity.setPatentStatus(PatentLifecycleStatus.ABANDONED);
            entity.setInReview(false);
            entity.setCurrentQuarterKey(null);
            patentMetadataRepository.save(entity);
        }

        PatentDetailResponse basePatent = new PatentDetailResponse(
                entity.getPatentId(),
                entity.getManagementNumber(),
                entity.getApplicationNumber(),
                entity.getRegistrationNumber(),
                entity.getTitle(),
                entity.getDraftTitle(),
                entity.getBusinessArea(),
                entity.getTechnologyArea(),
                entity.getProductName(),
                valueOrDefault(entity.getCountry(), "KR"),
                coApplicants(entity.getJointApplication(), entity.getCoApplicantName()),
                entity.getApplicationDate(),
                entity.getRegistrationDate(),
                expectedExpirationDate,
                departmentId(entity.getBusinessArea()),
                departmentName(entity.getBusinessArea()),
                ls,
                ReviewWorkflowStatus.NOT_IN_REVIEW,
                baseFeeDate,
                "연차료 납부 검토 시점 도래",
                Recommendation.HOLD,
                null,
                null,
                summaryFromMetadata(entity.getTitle(), entity.getTechnologyArea(), entity.getProductName()),
                aiEvaluationReport(Recommendation.HOLD),
                new FinalDecisionRecordResponse(null, null, null),
                new BusinessOpinionResponse(null, null, null),
                entity.isInReview());
        return applyPersistedState(basePatent);
    }

    private PatentDetailResponse newPatentFromRequest(String patentId, PatentUpsertRequest request) {
        LocalDate computedFeeDate = annualFeeScheduleService.calculateNextDueDate(
                request.country(), request.applicationDate(), request.registrationDate(), request.expectedExpirationDate());
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
                "",
                "",
                PatentLifecycleStatus.ACTIVE,
                ReviewWorkflowStatus.NOT_IN_REVIEW,
                computedFeeDate,
                "작성 필요",
                Recommendation.HOLD,
                null,
                null,
                new PatentSummaryResponse("작성 필요", "작성 필요", List.of(), "작성 필요", List.of()),
                aiEvaluationReport(Recommendation.HOLD),
                new FinalDecisionRecordResponse(null, null, null),
                new BusinessOpinionResponse(null, null, null),
                false);
    }

    private PatentMetadataEntity metadataEntityFromRequest(String patentId, PatentUpsertRequest request) {
        LocalDate computedFeeDate = annualFeeScheduleService.calculateNextDueDate(
                request.country(), request.applicationDate(), request.registrationDate(), request.expectedExpirationDate());
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
                PatentLifecycleStatus.ACTIVE,
                request.applicationDate(),
                request.registrationDate(),
                request.applicationNumber(),
                request.registrationNumber(),
                request.expectedExpirationDate(),
                computedFeeDate);
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
                patent.feeDueDate(),
                patent.reviewReason(),
                patent.currentRecommendation(),
                patent.businessOpinionDecision(),
                patent.legalActionResult(),
                patent.summary(),
                patent.aiEvaluationReport(),
                patent.finalDecisionRecord(),
                patent.businessOpinion(),
                patent.inReview());
    }

    private PatentDetailResponse withDepartment(PatentDetailResponse patent, String departmentId, String departmentName) {
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
                departmentId,
                departmentName,
                patent.lifecycleStatus(),
                patent.reviewWorkflowStatus(),
                patent.feeDueDate(),
                patent.reviewReason(),
                patent.currentRecommendation(),
                patent.businessOpinionDecision(),
                patent.legalActionResult(),
                patent.summary(),
                patent.aiEvaluationReport(),
                patent.finalDecisionRecord(),
                patent.businessOpinion(),
                patent.inReview());
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
        if ("Y".equalsIgnoreCase(jointApplication) && coApplicantName != null && !coApplicantName.isBlank()) {
            return coApplicantName;
        }
        if ("Y".equalsIgnoreCase(jointApplication)) {
            return "정보 부족 있음";
        }
        return "없음";
    }

    String departmentId(String businessArea) {
        return switch (valueOrDefault(businessArea, "")) {
            case "AI", "Data" -> "DEPT-RND";
            case "Blockchain" -> "DEPT-PLATFORM";
            case "ESG" -> "DEPT-ESG";
            case "통신" -> "DEPT-ICT";
            case "제조" -> "DEPT-MFG";
            default -> "DEPT-BIZ";
        };
    }

    String departmentName(String businessArea) {
        return switch (valueOrDefault(businessArea, "")) {
            case "AI", "Data" -> "R&D본부";
            case "Blockchain" -> "플랫폼사업부";
            case "ESG" -> "ESG사업부";
            case "통신" -> "ICT사업부";
            case "제조" -> "제조사업부";
            default -> "사업기획팀";
        };
    }

    private PatentLifecycleStatus lifecycleStatus(String status) {
        if (status == null) return PatentLifecycleStatus.ACTIVE;
        return switch (status) {
            case "등록", "유지", "ACTIVE" -> PatentLifecycleStatus.ACTIVE;
            case "소멸", "EXPIRED" -> PatentLifecycleStatus.EXPIRED;
            case "포기", "ABANDONED" -> PatentLifecycleStatus.ABANDONED;
            default -> PatentLifecycleStatus.ACTIVE;
        };
    }

    private PatentLifecycleStatus lifecycleStatusByLegalAction(LegalActionResult legalActionResult) {
        return switch (legalActionResult) {
            case MAINTAINED -> PatentLifecycleStatus.ACTIVE;
            case ABANDONED -> PatentLifecycleStatus.ABANDONED;
        };
    }

    private Recommendation recommendation(int sequence) {
        Recommendation[] recommendations = {
                Recommendation.MAINTAIN,
                Recommendation.REVIEW_AGAIN,
                Recommendation.ABANDON
        };
        return recommendations[(sequence - 1) % recommendations.length];
    }

    private ReviewWorkflowStatus workflowStatus(int sequence) {
        ReviewWorkflowStatus[] statuses = {
                ReviewWorkflowStatus.REVIEW_QUARTER_STARTED,
                ReviewWorkflowStatus.MAIL_READY,
                ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE,
                ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED,
                ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED
        };
        return statuses[(sequence - 1) % statuses.length];
    }

    private BusinessOpinionDecision businessOpinionDecision(int sequence, ReviewWorkflowStatus workflowStatus) {
        if (workflowStatus != ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED) {
            return null;
        }
        if (sequence % 6 == 0) {
            return BusinessOpinionDecision.ABANDON;
        }
        if (sequence % 4 == 0) {
            return BusinessOpinionDecision.MAINTAIN;
        }
        return null;
    }

    private String defaultBusinessOpinionReason(BusinessOpinionDecision decision) {
        return decision == BusinessOpinionDecision.MAINTAIN
                ? "사업부 검토 결과 현재 제품 또는 향후 로드맵과 연결성이 확인되어 유지 의견을 제출했습니다."
                : "현재 사업 적용 계획과 활용 근거가 부족해 포기 의견을 제출했습니다.";
    }

    private String defaultFinalDecisionReason(LegalActionResult result) {
        return switch (result) {
            case MAINTAINED -> "사업부 의견과 AI 평가 근거를 검토해 유지 처리했습니다.";
            case ABANDONED -> "사업부 의견과 AI 평가 근거를 검토해 포기 처리했습니다.";
        };
    }

    private LegalActionResult legalActionResult(ReviewWorkflowStatus status, Recommendation recommendation) {
        return null;
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

    private String resolvedDepartmentName(String departmentId) {
        if (departmentId == null || departmentId.isBlank()) {
            return "미지정";
        }
        return departmentNameCache.getOrDefault(departmentId, departmentId);
    }

    public void refreshDepartmentCache() {
        this.departmentNameCache = mailingRecipientMappingRepository.findAll().stream()
                .collect(Collectors.toMap(
                        DepartmentEntity::getDepartmentId,
                        DepartmentEntity::getDepartmentName,
                        (a, b) -> a,
                        HashMap::new));
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
                        new EvaluationScoreResponse(EvaluationCategory.BUSINESS_ALIGNMENT, 72,
                                "관련사업 분야와 기술 영역은 연결되지만 실제 제품 적용 여부는 추가 확인이 필요합니다.")),
                List.of("시장 규모 자료", "제품 적용 여부"),
                null,
                null);
    }

    private PatentListItemResponse toListItem(PatentDetailResponse detail) {
        return toListItem(detail, null);
    }

    private PatentListItemResponse toListItem(PatentDetailResponse detail, String currentQuarterKey) {
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
                detail.feeDueDate(),
                detail.reviewReason(),
                detail.currentRecommendation(),
                detail.businessOpinionDecision(),
                detail.legalActionResult(),
                originalPatentUrl(detail.country(), detail.applicationNumber(), detail.registrationNumber()),
                detail.inReview(),
                currentQuarterKey);
    }

    private String originalPatentUrl(String country, String applicationNumber, String registrationNumber) {
        String number = firstNonBlank(registrationNumber, applicationNumber);
        if (number == null) {
            return null;
        }
        String normalizedCountry = country == null || country.isBlank() ? "KR" : country.trim().toUpperCase(Locale.ROOT);
        String normalizedNumber = number.replaceAll("[^0-9A-Za-z]", "");
        if (normalizedNumber.isBlank()) {
            return null;
        }
        return "https://patents.google.com/patent/" + normalizedCountry + normalizedNumber + "/ko";
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
                PatentListItemResponse::feeDueDate,
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

    private String firstNonBlank(String firstValue, String secondValue) {
        if (firstValue != null && !firstValue.isBlank()) {
            return firstValue;
        }
        if (secondValue != null && !secondValue.isBlank()) {
            return secondValue;
        }
        return null;
    }

    private boolean lowerEquals(String value, String lowerKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).equals(lowerKeyword);
    }

    private PatentDetailResponse applyPersistedState(PatentDetailResponse patent) {
        List<PatentReviewHistoryEntity> history =
                reviewHistoryRepository.findByPatentIdOrderByCreatedAtDesc(patent.patentId());
        if (!history.isEmpty()) {
            return applyHistoryState(patent, history.get(0));
        }
        return patent;
    }

    private PatentDetailResponse applyHistoryState(PatentDetailResponse patent, PatentReviewHistoryEntity state) {
        Recommendation currentRecommendation = state.getAiRecommendation() != null
                ? state.getAiRecommendation()
                : patent.currentRecommendation();
        AiEvaluationReportResponse aiReport = aiReportFromHistory(state, patent.aiEvaluationReport(), currentRecommendation);
        PatentSummaryResponse summary = summaryFromHistory(state, patent.summary());
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
                state.getDepartmentId() != null ? state.getDepartmentId() : "",
                resolvedDepartmentName(state.getDepartmentId()),
                patent.lifecycleStatus(),
                state.getReviewWorkflowStatus() != null ? state.getReviewWorkflowStatus() : patent.reviewWorkflowStatus(),
                state.getAnnualFeeDueDate() != null ? state.getAnnualFeeDueDate() : patent.feeDueDate(),
                patent.reviewReason(),
                currentRecommendation,
                state.getBusinessOpinionDecision() != null ? state.getBusinessOpinionDecision() : patent.businessOpinionDecision(),
                state.getLegalActionResult() != null ? state.getLegalActionResult() : patent.legalActionResult(),
                summary,
                aiReport,
                new FinalDecisionRecordResponse(
                        state.getFinalDecisionId(),
                        state.getFinalDecisionReason(),
                        state.getFinalDecisionDecidedAt()),
                new BusinessOpinionResponse(
                        state.getBusinessOpinionDecision(),
                        state.getBusinessOpinionReason(),
                        state.getBusinessOpinionSubmittedAt()),
                patent.inReview());
    }

    private AiEvaluationReportResponse aiReportFromHistory(
            PatentReviewHistoryEntity state,
            AiEvaluationReportResponse fallback,
            Recommendation recommendation
    ) {
        if (state.getAiReportId() == null) {
            return withAiRecommendation(fallback, recommendation);
        }
        return new AiEvaluationReportResponse(
                state.getAiReportId(),
                state.getAiReportCreatedAt(),
                recommendation,
                state.getAiRecommendationReason(),
                state.getAiTotalScore(),
                readEvaluationScores(state.getAiScoresJson()),
                readStringList(state.getAiMissingInformationJson()),
                state.getAiReportMarkdown(),
                state.getAiReportMarkdownPath());
    }

    private AiEvaluationReportResponse withAiRecommendation(
            AiEvaluationReportResponse report,
            Recommendation recommendation
    ) {
        return new AiEvaluationReportResponse(
                report.reportId(),
                report.createdAt(),
                recommendation,
                report.recommendationReason(),
                report.totalScore(),
                report.scores(),
                report.missingInformation(),
                report.rawMarkdown(),
                report.markdownFilePath());
    }

    private void applyAiReportToHistory(PatentReviewHistoryEntity state, AiEvaluationReportResponse report) {
        state.setAiReportId(report.reportId());
        state.setAiReportCreatedAt(report.createdAt());
        state.setAiRecommendation(report.recommendation());
        state.setAiRecommendationReason(report.recommendationReason());
        state.setAiTotalScore(report.totalScore());
        state.setAiScoresJson(writeJson(report.scores()));
        state.setAiMissingInformationJson(writeJson(report.missingInformation()));
        state.setAiReportMarkdown(report.rawMarkdown());
        state.setAiReportMarkdownPath(report.markdownFilePath());
    }

    private PatentSummaryResponse summaryFromHistory(PatentReviewHistoryEntity state, PatentSummaryResponse fallback) {
        if (state.getSummaryText() == null) {
            return fallback;
        }
        return new PatentSummaryResponse(
                state.getSummaryText(),
                state.getSummaryProblemSolved(),
                readStringList(state.getSummaryCoreTechnicalPointsJson()),
                state.getSummaryClaims(),
                readStringList(state.getSummaryMissingFieldsJson()));
    }

    private void applySummaryToHistory(PatentReviewHistoryEntity state, PatentSummaryResponse summary) {
        state.setSummaryText(summary.summaryText());
        state.setSummaryProblemSolved(summary.problemSolved());
        state.setSummaryCoreTechnicalPointsJson(writeJson(summary.coreTechnicalPoints()));
        state.setSummaryClaims(summary.claimsSummary());
        state.setSummaryMissingFieldsJson(writeJson(summary.missingFields()));
    }

    private List<EvaluationScoreResponse> readEvaluationScores(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<String> readStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("AI 평가 레포트 JSON을 저장할 수 없습니다.", exception);
        }
    }

    private void persistPatentState(PatentDetailResponse patent) {
        List<PatentReviewHistoryEntity> history =
                reviewHistoryRepository.findByPatentIdOrderByCreatedAtDesc(patent.patentId());
        PatentReviewHistoryEntity state = history.isEmpty()
                ? new PatentReviewHistoryEntity(patent.patentId(), "UNQUARTERED")
                : history.get(0);
        state.setReviewWorkflowStatus(patent.reviewWorkflowStatus());
        state.setAiRecommendation(patent.currentRecommendation());
        applyAiReportToHistory(state, patent.aiEvaluationReport());
        applySummaryToHistory(state, patent.summary());
        state.setBusinessOpinionDecision(patent.businessOpinionDecision());
        state.setBusinessOpinionReason(patent.businessOpinion().reason());
        state.setBusinessOpinionSubmittedAt(patent.businessOpinion().submittedAt());
        state.setLegalActionResult(patent.legalActionResult());
        state.setFinalDecisionId(patent.finalDecisionRecord().decisionId());
        state.setFinalDecisionReason(patent.finalDecisionRecord().reason());
        state.setFinalDecisionDecidedAt(patent.finalDecisionRecord().decidedAt());
        state.setAnnualFeeDueDate(patent.feeDueDate());
        state.setDepartmentId(patent.departmentId());
        state.setDepartmentName(patent.departmentName());
        patentMetadataRepository.findById(patent.patentId()).ifPresent(entity -> {
            entity.setFeeDueDate(patent.feeDueDate());
            entity.setPatentStatus(patent.lifecycleStatus());
            entity.setInReview(patent.inReview());
            if (!patent.inReview()) {
                entity.setCurrentQuarterKey(null);
            }
            patentMetadataRepository.save(entity);
        });
        reviewHistoryRepository.save(state);
    }

    private PatentReviewHistoryItemResponse toHistoryItem(PatentReviewHistoryEntity entity) {
        return new PatentReviewHistoryItemResponse(
                entity.getQuarterKey(),
                nameOrNull(entity.getReviewWorkflowStatus()),
                nameOrNull(entity.getBusinessOpinionDecision()),
                entity.getBusinessOpinionReason(),
                entity.getBusinessOpinionSubmittedAt(),
                nameOrNull(entity.getLegalActionResult()),
                entity.getFinalDecisionId(),
                entity.getFinalDecisionReason(),
                entity.getFinalDecisionDecidedAt(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().atZone(KST).toOffsetDateTime() : null,
                entity.getDepartmentId(),
                entity.getDepartmentName());
    }

    private String nameOrNull(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private <T extends Enum<T>> T enumOrDefault(Class<T> enumType, String value, T defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            return defaultValue;
        }
    }

    private interface PatentUpdater {
        PatentDetailResponse update(PatentDetailResponse patent);
    }

}
