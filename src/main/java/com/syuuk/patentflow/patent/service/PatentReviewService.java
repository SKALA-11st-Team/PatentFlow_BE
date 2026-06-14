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
import com.syuuk.patentflow.patent.dto.AiReportReadinessStatus;
import com.syuuk.patentflow.patent.dto.AiReportOverrides;
import com.syuuk.patentflow.patent.dto.SourceResponse;
import com.syuuk.patentflow.patent.dto.BusinessOpinionDecision;
import com.syuuk.patentflow.patent.dto.BusinessOpinionResponse;
import com.syuuk.patentflow.patent.dto.CoApplicantConsentRequest;
import com.syuuk.patentflow.patent.dto.CoApplicantConsentResponse;
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
import com.syuuk.patentflow.patent.dto.PatentFilterOptionsResponse;
import com.syuuk.patentflow.patent.dto.PatentHistoryResponse;
import com.syuuk.patentflow.patent.dto.PatentLifecycleStatus;
import com.syuuk.patentflow.patent.dto.PatentListFilter;
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
import com.syuuk.patentflow.patent.client.AiReportAgentClient;
import com.syuuk.patentflow.common.dto.ClassificationResponse;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AiReportAgentClient aiReportAgentClient;
    private final SystemSettingsService systemSettingsService;
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
            PatentLookupService lookupService,
            AiReportAgentClient aiReportAgentClient,
            SystemSettingsService systemSettingsService
    ) {
        this.aiReportAgentClient = aiReportAgentClient;
        this.systemSettingsService = systemSettingsService;
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
    @Transactional
    public PageResponse<PatentListItemResponse> getPatents(int page, int size, String sort, PatentListFilter filter) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);

        // 과기 ACTIVE 특허를 ABANDONED로 일괄 보정 — 기존 단건 로딩 부수효과를 페이징 경로에서도 유지한다.
        correctOverdueActivePatents();

        Pageable pageable = PageRequest.of(normalizedPage - 1, normalizedSize, listSort(sort));
        Page<PatentMetadataEntity> entityPage = patentMetadataRepository.findAll(
                patentListSpecification(filter), pageable);

        Map<String, PatentReviewHistoryEntity> latestHistory = loadLatestHistory(entityPage.getContent());
        List<PatentListItemResponse> items = entityPage.getContent().stream()
                .map(entity -> toListItem(
                        patentFromMetadataEntity(entity, latestHistory.get(entity.getPatentId())),
                        entity.getCurrentQuarterKey()))
                .toList();

        return PageResponse.ok(
                items,
                new PageInfo(normalizedPage, normalizedSize,
                        entityPage.getTotalElements(), entityPage.getTotalPages()));
    }

    /**
     * CONTRACT-09/DASH-08: 검토 대상 목록 화면 드롭다운용 필터 옵션. 전체 특허 기준 distinct 값이라
     * 서버 필터링으로 목록이 부분집합이 되어도 옵션은 줄지 않는다.
     */
    @Transactional(readOnly = true)
    public PatentFilterOptionsResponse getFilterOptions() {
        return new PatentFilterOptionsResponse(
                patentMetadataRepository.findDistinctCountries(),
                patentMetadataRepository.findDistinctBusinessAreas(),
                patentMetadataRepository.findDistinctTechnologyAreas(),
                patentMetadataRepository.findDistinctProductNames());
    }

    /**
     * 목록 검색/필터를 DB 레벨에서 처리하는 Specification.
     * keyword/영역(사업·기술·제품)/국가/분기/날짜/검토여부는 metadata 컬럼으로, departmentId·
     * reviewWorkflowStatus는 특허별 최신 이력 행을 가리키는 EXISTS 서브쿼리로 필터링한다.
     */
    private Specification<PatentMetadataEntity> patentListSpecification(PatentListFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            addKeywordPredicate(predicates, cb, root, filter.keyword());
            if (filter.departmentId() != null && !filter.departmentId().isBlank()) {
                predicates.add(latestHistoryMatches(root, query, cb, "departmentId", filter.departmentId()));
            }
            if (filter.reviewWorkflowStatus() != null) {
                predicates.add(latestHistoryMatches(root, query, cb, "reviewWorkflowStatus", filter.reviewWorkflowStatus()));
            }
            addCountryPredicate(predicates, cb, root, filter.country());
            addDatePredicates(predicates, cb, root, filter.dateFrom(), filter.dateTo());
            addQuarterPredicate(predicates, cb, root, filter.quarter());
            addContextPredicate(predicates, cb, root, "businessArea", filter.businessArea());
            addContextPredicate(predicates, cb, root, "technologyArea", filter.technologyArea());
            addContextPredicate(predicates, cb, root, "productName", filter.productName());
            if (filter.inReview() != null) {
                predicates.add(cb.equal(root.get("inReview"), filter.inReview()));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void addKeywordPredicate(
            List<Predicate> predicates, CriteriaBuilder cb, Root<PatentMetadataEntity> root, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        // I5: 통합 검색 — 번호·제목 외에 초안 제목, 관련 제품/사업/기술 분야까지 한 번에 찾는다.
        predicates.add(cb.or(
                cb.like(cb.lower(cb.coalesce(root.get("title"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("draftTitle"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("managementNumber"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("applicationNumber"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("registrationNumber"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("productName"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("businessArea"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("technologyArea"), "")), like)));
    }

    private void addCountryPredicate(
            List<Predicate> predicates, CriteriaBuilder cb, Root<PatentMetadataEntity> root, String country) {
        if (country == null) {
            return;
        }
        String normalized = country.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank() || "ALL".equals(normalized)) {
            return;
        }
        predicates.add(cb.equal(cb.upper(cb.coalesce(root.get("country"), "")), normalized));
    }

    private void addDatePredicates(
            List<Predicate> predicates, CriteriaBuilder cb, Root<PatentMetadataEntity> root,
            LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("feeDueDate"), dateFrom));
        }
        if (dateTo != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("feeDueDate"), dateTo));
        }
    }

    private void addQuarterPredicate(
            List<Predicate> predicates, CriteriaBuilder cb, Root<PatentMetadataEntity> root, String quarter) {
        if (quarter == null) {
            return;
        }
        String normalized = quarter.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank() || "ALL".equals(normalized)) {
            return;
        }
        int quarterNumber = quarterNumber(normalized);
        if (quarterNumber > 0) {
            LocalDate quarterStart = LocalDate.of(LocalDate.now(KST).getYear(), (quarterNumber - 1) * 3 + 1, 1);
            LocalDate quarterEnd = quarterStart.plusMonths(3).minusDays(1);
            predicates.add(cb.between(root.get("feeDueDate"), quarterStart, quarterEnd));
        } else {
            predicates.add(cb.or(
                    cb.equal(root.get("currentQuarterKey"), normalized),
                    cb.like(root.get("currentQuarterKey"), "%-" + normalized)));
        }
    }

    /** 영역 정확 일치 필터. "미분류"는 FE 표시 정규화와 동일하게 공백/"N/A"/null 을 매칭한다. */
    private void addContextPredicate(
            List<Predicate> predicates, CriteriaBuilder cb, Root<PatentMetadataEntity> root,
            String field, String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.trim())) {
            return;
        }
        String trimmed = value.trim();
        if ("미분류".equals(trimmed)) {
            Expression<String> normalized = cb.trim(cb.coalesce(root.get(field), ""));
            predicates.add(cb.or(cb.equal(normalized, ""), cb.equal(cb.upper(normalized), "N/A")));
        } else {
            predicates.add(cb.equal(root.get(field), trimmed));
        }
    }

    /**
     * 특허의 최신 이력 행에서 지정한 필드가 주어진 값과 일치하는지 EXISTS 서브쿼리로 검사한다.
     */
    private Predicate latestHistoryMatches(
            Root<PatentMetadataEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb,
            String field, Object value) {
        Subquery<String> sub = query.subquery(String.class);
        Root<PatentReviewHistoryEntity> history = sub.from(PatentReviewHistoryEntity.class);

        Subquery<LocalDateTime> latest = sub.subquery(LocalDateTime.class);
        Root<PatentReviewHistoryEntity> latestHistory = latest.from(PatentReviewHistoryEntity.class);
        latest.select(cb.greatest(latestHistory.<LocalDateTime>get("createdAt")))
                .where(cb.equal(latestHistory.get("patentId"), history.get("patentId")));

        sub.select(history.get("patentId"))
                .where(cb.and(
                        cb.equal(history.get("patentId"), root.get("patentId")),
                        cb.equal(history.get(field), value),
                        cb.equal(history.get("createdAt"), latest)));

        return cb.exists(sub);
    }

    /**
     * 정렬 파라미터를 metadata 컬럼 기준 Sort로 변환한다. 기본값은 연차료 납부 기준일 오름차순.
     */
    private Sort listSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "feeDueDate");
        }
        String[] parts = sort.split(",");
        String property = switch (parts[0]) {
            case "title" -> "title";
            case "managementNumber" -> "managementNumber";
            default -> "feeDueDate";
        };
        Sort.Direction direction = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1]))
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }

    /**
     * 연차료 납부 기준일이 지난 보유 중 특허를 소멸(EXPIRED) 상태로 일괄 보정한다.
     * 사람이 기록한 포기(ABANDONED='포기 완료')와 달리, 기준일 경과만으로 자동 전환되는 경로는
     * '소멸'(EXPIRED) 의미를 갖는다 — 두 라벨이 섞이지 않도록 자동 보정은 EXPIRED로 둔다.
     */
    private void correctOverdueActivePatents() {
        List<PatentMetadataEntity> overdue = patentMetadataRepository
                .findByPatentStatusAndFeeDueDateBefore(PatentLifecycleStatus.ACTIVE, LocalDate.now(KST));
        if (overdue.isEmpty()) {
            return;
        }
        overdue.forEach(entity -> {
            entity.setPatentStatus(PatentLifecycleStatus.EXPIRED);
            entity.setInReview(false);
            entity.setCurrentQuarterKey(null);
        });
        patentMetadataRepository.saveAll(overdue);
    }

    /**
     * 주어진 특허들의 최신 이력 행을 한 번의 쿼리로 적재해 patentId로 매핑한다.
     */
    private Map<String, PatentReviewHistoryEntity> loadLatestHistory(List<PatentMetadataEntity> entities) {
        if (entities.isEmpty()) {
            return Map.of();
        }
        List<String> patentIds = entities.stream().map(PatentMetadataEntity::getPatentId).toList();
        return reviewHistoryRepository.findLatestByPatentIds(patentIds).stream()
                .collect(Collectors.toMap(
                        PatentReviewHistoryEntity::getPatentId,
                        history -> history,
                        (existing, ignored) -> existing,
                        HashMap::new));
    }

    @Transactional
    public List<PatentListItemResponse> getReviewTargets(
            String quarter,
            String country,
            LocalDate dateFrom,
            LocalDate dateTo,
            ReviewWorkflowStatus reviewWorkflowStatus) {
        // 과기 ACTIVE 특허를 EXPIRED로 일괄 보정 — getPatents와 동일하게 쓰기 트랜잭션에서 수행한다.
        // (조회 매핑은 부수효과 없이 멱등하므로 보정은 이 진입점에서 일관되게 일으킨다.)
        correctOverdueActivePatents();
        String normalizedQuarter = quarter == null ? null : quarter.trim().toUpperCase(Locale.ROOT);
        String normalizedCountry = country == null ? null : country.trim().toUpperCase(Locale.ROOT);

        List<PatentMetadataEntity> entities = patentMetadataRepository.findAll(reviewTargetSpecification(
                normalizedQuarter, normalizedCountry, dateFrom, dateTo, reviewWorkflowStatus),
                Sort.by(Sort.Direction.ASC, "feeDueDate", "managementNumber"));
        Map<String, PatentReviewHistoryEntity> latestHistory = loadLatestHistory(entities);
        return entities.stream()
                .map(entity -> toListItem(
                        patentFromMetadataEntity(entity, latestHistory.get(entity.getPatentId())),
                        entity.getCurrentQuarterKey()))
                .toList();
    }

    private Specification<PatentMetadataEntity> reviewTargetSpecification(
            String normalizedQuarter,
            String normalizedCountry,
            LocalDate dateFrom,
            LocalDate dateTo,
            ReviewWorkflowStatus reviewWorkflowStatus) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (reviewWorkflowStatus != null) {
                predicates.add(latestHistoryMatches(root, query, cb, "reviewWorkflowStatus", reviewWorkflowStatus));
            }
            if (normalizedCountry != null && !normalizedCountry.isBlank() && !"ALL".equals(normalizedCountry)) {
                predicates.add(cb.equal(cb.upper(cb.coalesce(root.get("country"), "")), normalizedCountry));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("feeDueDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("feeDueDate"), dateTo));
            }
            if (normalizedQuarter != null && !normalizedQuarter.isBlank() && !"ALL".equals(normalizedQuarter)) {
                int quarterNumber = quarterNumber(normalizedQuarter);
                if (quarterNumber > 0) {
                    LocalDate quarterStart = LocalDate.of(LocalDate.now(KST).getYear(), (quarterNumber - 1) * 3 + 1, 1);
                    LocalDate quarterEnd = quarterStart.plusMonths(3).minusDays(1);
                    predicates.add(cb.between(root.get("feeDueDate"), quarterStart, quarterEnd));
                } else {
                    predicates.add(cb.or(
                            cb.equal(root.get("currentQuarterKey"), normalizedQuarter),
                            cb.like(root.get("currentQuarterKey"), "%-" + normalizedQuarter)));
                }
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private int quarterNumber(String quarter) {
        if (quarter == null || !quarter.matches("Q[1-4]")) {
            return -1;
        }
        return Integer.parseInt(quarter.substring(1));
    }

    public PageResponse<PatentListItemResponse> getReviewRequests(
            int page,
            int size,
            String departmentId
    ) {
        return getPatents(page, size, null, new PatentListFilter(
                null, departmentId, ReviewWorkflowStatus.WAITING_BUSINESS_RESPONSE,
                null, null, null, null, null, null, null, null));
    }

    /**
     * @relatedFR FR-LEGAL-05, FR-LEGAL-06, FR-LEGAL-07, FR-LEGAL-08, FR-LEGAL-09, FR-LEGAL-10
     * @relatedUI UI-LEGAL-04
     * @description AI 특허 평가 레포트와 최종 판단을 분리해 특허 상세를 조회한다.
     */
    @Transactional
    public PatentDetailResponse getPatentDetail(String patentId) {
        // 과기 ACTIVE 특허를 EXPIRED로 일괄 보정 — getPatents와 동일하게 쓰기 트랜잭션에서 수행한다.
        // (조회 매핑 patentFromMetadataEntity는 부수효과 없이 멱등하므로 보정은 진입점에서 일관되게 일으킨다.)
        correctOverdueActivePatents();
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

    /**
     * CONTRACT-02: 대표 종합 점수는 0~100 평균을 정본으로 한다(totalScore는 0~400 원문 합).
     * 사업부 제출 스냅샷 등 단일 대표값이 필요한 곳은 이 평균(반올림)을 사용해 화면 전반의 척도를 통일한다.
     */
    public Integer getAiAverageScore(String patentId) {
        Double averageScore = findPatent(patentId).aiEvaluationReport().averageScore();
        return averageScore == null ? null : (int) Math.round(averageScore);
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

        if (history.getCoApplicantConsentStatus() != null) {
            responses.add(new PatentHistoryResponse(
                    history.getId() + "-COAPPLICANT",
                    "CO_APPLICANT_CONSENT_RECORDED",
                    "공동출원인 합의 기록",
                    "공동출원인 합의가 %s(으)로 기록되었습니다.".formatted(history.getCoApplicantConsentStatus().name()),
                    valueOrDefault(history.getCoApplicantConsentDecidedBy(), "관리자"),
                    history.getCoApplicantConsentDecidedAt() != null ? history.getCoApplicantConsentDecidedAt() : createdAt));
        }

        if (history.getLegalActionResult() != null || history.getFinalDecisionDecidedAt() != null) {
            // REVIEW-07: 행위자를 '관리자' 하드코딩 대신 저장된 결정자(decidedBy)로 표기, 미상이면 폴백.
            String finalActor = valueOrDefault(history.getFinalDecisionDecidedBy(), "관리자");
            responses.add(new PatentHistoryResponse(
                    history.getId() + "-FINAL",
                    "FINAL_DECISION_RECORDED",
                    "최종 판단 기록",
                    "%s가 %s 결과를 기록했습니다.".formatted(
                            finalActor,
                            history.getLegalActionResult() == null ? "최종 판단" : history.getLegalActionResult().name()),
                    finalActor,
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

    /**
     * @relatedFR FR-LEGAL-04
     * @relatedUI UI-LEGAL-03, UI-LEGAL-04
     * @description 특허 분야 추천. 관리자 관리 분류(taxonomy)를 함께 AI 에이전트에 전달해 추천을 받고,
     * 에이전트 미가용/실패 시 기존 metadata 기반 in-memory 추천으로 폴백한다. (에이전트는 DB에 직접 접근하지 않는다)
     */
    public PatentContextSuggestionResponse suggestContext(PatentContextSuggestionRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", request.title());
        body.put("managementNumber", request.managementNumber());
        body.put("applicationNumber", request.applicationNumber());
        body.put("technologyArea", request.technologyArea());
        body.put("businessArea", request.businessArea());
        // CONTRACT-04: productName은 에이전트 추천 계약(분야 추천=사업/기술 분야)의 입력이 아니다.
        // 과거에는 본문/taxonomy로 보냈으나 에이전트가 묵살(FastAPI 미선언)하던 dead path여서 제거한다.
        body.put("taxonomy", buildClassificationTaxonomy());

        String patentRef = request.managementNumber() != null && !request.managementNumber().isBlank()
                ? request.managementNumber()
                : "new";
        AiReportAgentClient.AgentFieldRecommendation recommendation =
                aiReportAgentClient.recommendFields(patentRef, body);

        if (recommendation != null
                && ((recommendation.businessArea() != null && !recommendation.businessArea().isBlank())
                || (recommendation.technologyArea() != null && !recommendation.technologyArea().isBlank()))) {
            return new PatentContextSuggestionResponse(
                    recommendation.businessArea(),
                    recommendation.confidenceText(),
                    recommendation.reason(),
                    recommendation.technologyArea());
        }

        // 에이전트가 비정상/빈 응답이면 기존 metadata 키워드 매칭으로 폴백한다.
        return lookupService.suggestContext(request, loadPatentsFromDatabase());
    }

    /**
     * 관리자 관리 분류값을 에이전트가 이해하는 taxonomy 형태로 변환한다.
     * (CONTRACT-04: 추천 대상은 사업/기술 분야뿐 — productName 분류는 에이전트가 쓰지 않아 제외)
     */
    private Map<String, List<String>> buildClassificationTaxonomy() {
        Map<String, List<String>> taxonomy = new HashMap<>();
        taxonomy.put("businessArea", List.of());
        taxonomy.put("technologyArea", List.of());
        for (ClassificationResponse classification : systemSettingsService.getClassifications()) {
            if ("BUSINESS".equals(classification.type())) {
                taxonomy.put("businessArea", classification.values());
            } else if ("TECHNOLOGY".equals(classification.type())) {
                taxonomy.put("technologyArea", classification.values());
            }
        }
        return taxonomy;
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
        // 기본정보 컬럼은 metadataEntityFromRequest가 갱신하지만, 그 생성자는 inReview=false·
        // patentStatus=ACTIVE·currentQuarterKey=null로 초기화한다. 직전 updatePatent→persistPatentState가
        // 보존한 워크플로우 상태(검토 대상 여부·포기/소멸 라벨·분기 키)를 새 엔티티에 복원한 뒤 저장한다.
        PatentMetadataEntity updated = metadataEntityFromRequest(patentId, request);
        patentMetadataRepository.findById(patentId).ifPresent(existing -> {
            updated.setPatentStatus(existing.getPatentStatus());
            updated.setInReview(existing.isInReview());
            updated.setCurrentQuarterKey(existing.getCurrentQuarterKey());
        });
        patentMetadataRepository.save(updated);
        return new PatentUpsertResponse(patentId, "UPDATED");
    }

    private String nextPatentId() {
        int nextSequence = patentMetadataRepository.findMaxPatentSequence() + 1;
        return "PAT-2026-%04d".formatted(nextSequence);
    }

    public PatentDetailResponse assignDepartment(String patentId, String departmentId) {
        // 참조 무결성: 존재하지 않는 departmentId를 부서명으로 폴백 저장하지 않도록 사전 검증한다.
        // (없으면 단건은 명확한 오류, bulk는 Controller try/catch로 건별 failed 분류된다.)
        if (departmentId != null && !departmentId.isBlank()
                && !mailingRecipientMappingRepository.existsById(departmentId)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "존재하지 않는 사업부입니다: " + departmentId);
        }
        String departmentName = resolvedDepartmentName(departmentId);
        PatentDetailResponse updated = updatePatent(patentId, patent -> withDepartment(patent, departmentId, departmentName));
        persistPatentState(updated);
        return updated;
    }

    /**
     * @relatedFR FR-LEGAL-05
     * F6: 특허 패밀리 — 관리번호의 국가 접미사 앞 공통 계열(P201103001-KR0 → P201103001-*)을
     * 묶어 같은 발명의 국가별 출원을 한눈에 본다. 계열 구분자가 없으면 자신만 반환한다.
     */
    @Transactional(readOnly = true)
    public List<PatentListItemResponse> getPatentFamily(String patentId) {
        PatentMetadataEntity self = patentMetadataRepository.findById(patentId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.PATENT_NOT_FOUND));
        String managementNumber = self.getManagementNumber() == null ? "" : self.getManagementNumber();
        int separatorIndex = managementNumber.lastIndexOf('-');
        if (separatorIndex <= 0) {
            return List.of();
        }
        String familyPrefix = managementNumber.substring(0, separatorIndex + 1);
        List<PatentMetadataEntity> members =
                patentMetadataRepository.findByManagementNumberStartingWithOrderByManagementNumberAsc(familyPrefix);
        Map<String, PatentReviewHistoryEntity> latestHistory = loadLatestHistory(members);
        return members.stream()
                .filter(entity -> !entity.getPatentId().equals(patentId))
                .map(entity -> toListItem(
                        patentFromMetadataEntity(entity, latestHistory.get(entity.getPatentId())),
                        entity.getCurrentQuarterKey()))
                .toList();
    }

    public List<PatentListItemResponse> getAllPatents() {
        List<PatentMetadataEntity> entities = patentMetadataRepository.findAll(Sort.by("patentId"));
        Map<String, PatentReviewHistoryEntity> latestHistory = loadLatestHistory(entities);
        return entities.stream()
                .map(entity -> toListItem(
                        patentFromMetadataEntity(entity, latestHistory.get(entity.getPatentId())),
                        entity.getCurrentQuarterKey()))
                .toList();
    }

    public List<String> createQuarterReviewTargets(
            String quarterKey, LocalDate paymentPeriodStart, LocalDate paymentPeriodEnd
    ) {
        return workflowService.createQuarterReviewTargets(quarterKey, paymentPeriodStart, paymentPeriodEnd);
    }

    public List<PatentReviewHistoryItemResponse> getReviewHistory(String patentId) {
        ensurePatentExists(patentId);
        return reviewHistoryRepository.findByPatentIdOrderByCreatedAtDesc(patentId).stream()
                .map(this::toHistoryItem)
                .toList();
    }


    public FinalDecisionResponse patchFinalDecision(String patentId, PatchFinalDecisionRequest request, String actor) {
        return workflowService.patchFinalDecision(patentId, request, actor);
    }

    public FinalDecisionResponse recordFinalDecision(String patentId, FinalDecisionRequest request, String actor) {
        return workflowService.recordFinalDecision(patentId, request, actor);
    }

    public PatentDetailResponse recordCoApplicantConsent(
            String patentId, CoApplicantConsentRequest request, String actor) {
        return workflowService.recordCoApplicantConsent(patentId, request, actor);
    }

    PatentDetailResponse findPatent(String patentId) {
        return patentMetadataRepository.findById(patentId)
                .map(this::patentFromMetadataEntity)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.PATENT_NOT_FOUND));
    }

    PatentDetailResponse findPatentOrNull(String patentId) {
        return patentMetadataRepository.findById(patentId)
                .map(this::patentFromMetadataEntity)
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
        List<PatentMetadataEntity> entities = patentMetadataRepository.findAll(Sort.by("patentId"));
        Map<String, PatentReviewHistoryEntity> latestHistory = loadLatestHistory(entities);
        return entities.stream()
                .map(entity -> patentFromMetadataEntity(entity, latestHistory.get(entity.getPatentId())))
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
        LocalDate applicationDate = parseDate(columns.get(10));
        LocalDate registrationDate = parseDate(columns.get(11));
        LocalDate expectedExpirationDate = parseDate(columns.get(14));
        LocalDate feeDueDate = annualFeeScheduleService.calculateNextDueDate(
                columns.get(6), applicationDate, registrationDate, expectedExpirationDate);
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
                applicationDate,
                registrationDate,
                columns.get(12),
                columns.get(13),
                expectedExpirationDate,
                feeDueDate);
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
        return patentFromMetadataEntity(entity, latestHistoryOrNull(entity.getPatentId()));
    }

    private PatentDetailResponse patentFromMetadataEntity(PatentMetadataEntity entity, PatentReviewHistoryEntity latestOrNull) {
        PatentLifecycleStatus ls = entity.getPatentStatus();
        LocalDate expectedExpirationDate = entity.getExpectedExpirationDate();
        LocalDate baseFeeDate = entity.getFeeDueDate() != null
                ? entity.getFeeDueDate()
                : annualFeeScheduleService.calculateNextDueDate(
                        entity.getCountry(), entity.getApplicationDate(),
                        entity.getRegistrationDate(), expectedExpirationDate);

        // 기준일 경과 보유 중 특허는 응답상 소멸(EXPIRED)로 표시한다. 영속 보정은
        // correctOverdueActivePatents()가 쓰기 트랜잭션에서 일괄 수행한다 — 조회(매핑)는 부수효과 없이
        // 멱등하게 둔다(읽기가 write를 유발하지 않도록). 자동 경로는 '소멸', 사람 결정은 '포기(ABANDONED)'.
        if (ls == PatentLifecycleStatus.ACTIVE && baseFeeDate != null
                && baseFeeDate.isBefore(LocalDate.now(KST))) {
            ls = PatentLifecycleStatus.EXPIRED;
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
                Recommendation.REVIEW_AGAIN,
                null,
                null,
                summaryFromMetadata(entity.getTitle(), entity.getTechnologyArea(), entity.getProductName()),
                aiEvaluationReport(Recommendation.REVIEW_AGAIN),
                new FinalDecisionRecordResponse(null, null, null, null),
                new BusinessOpinionResponse(null, null, null),
                entity.isInReview(),
                isJointApplication(entity.getJointApplication()), null);
        return applyPersistedState(basePatent, latestOrNull);
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
                Recommendation.REVIEW_AGAIN,
                null,
                null,
                new PatentSummaryResponse("작성 필요", "작성 필요", List.of(), "작성 필요", List.of()),
                aiEvaluationReport(Recommendation.REVIEW_AGAIN),
                new FinalDecisionRecordResponse(null, null, null, null),
                new BusinessOpinionResponse(null, null, null),
                false,
                false, null);
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
                patent.inReview(),
                patent.jointApplication(), patent.coApplicantConsent());
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
                patent.inReview(),
                patent.jointApplication(), patent.coApplicantConsent());
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

    private boolean isJointApplication(String jointApplication) {
        return "Y".equalsIgnoreCase(jointApplication);
    }

    /** 이력 행의 공동출원인 합의 필드를 응답으로 변환. 합의 미기록(status null)이면 null. */
    private CoApplicantConsentResponse coApplicantConsentFromHistory(PatentReviewHistoryEntity state) {
        if (state.getCoApplicantConsentStatus() == null) {
            return null;
        }
        return new CoApplicantConsentResponse(
                state.getCoApplicantConsentStatus(),
                state.getCoApplicantConsentReason(),
                state.getCoApplicantConsentDecidedAt(),
                state.getCoApplicantConsentDecidedBy());
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
                72.0,
                null,
                false,
                null,
                List.of(
                        new EvaluationScoreResponse(EvaluationCategory.RIGHTS, 70, null,
                                "청구항 범위는 확인되나 일부 권리 범위 비교 자료가 부족합니다.", List.of()),
                        new EvaluationScoreResponse(EvaluationCategory.TECHNOLOGY, 78, null, "명세서상 기술적 차별 요소가 확인됩니다.", List.of()),
                        new EvaluationScoreResponse(EvaluationCategory.MARKET, null, null, "시장 규모 자료가 부족하여 추가 확인이 필요합니다.", List.of()),
                        new EvaluationScoreResponse(EvaluationCategory.BUSINESS_ALIGNMENT, 72, null,
                                "관련사업 분야와 기술 영역은 연결되지만 실제 제품 적용 여부는 추가 확인이 필요합니다.", List.of())),
                List.of("시장 규모 자료", "제품 적용 여부"),
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of());
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
                aiReportReadinessStatus(detail),
                aiReportFailureReason(detail),
                originalPatentUrl(detail.country(), detail.applicationNumber(), detail.registrationNumber()),
                detail.inReview(),
                currentQuarterKey);
    }

    private AiReportReadinessStatus aiReportReadinessStatus(PatentDetailResponse detail) {
        AiEvaluationReportResponse report = detail.aiEvaluationReport();
        if (report != null && (report.degraded() || hasText(report.failureReason()))) {
            return AiReportReadinessStatus.FAILED;
        }
        if (detail.reviewWorkflowStatus() == ReviewWorkflowStatus.MAIL_READY
                && report != null
                && hasText(report.reportId())) {
            return AiReportReadinessStatus.READY;
        }
        return AiReportReadinessStatus.PENDING;
    }

    private String aiReportFailureReason(PatentDetailResponse detail) {
        AiEvaluationReportResponse report = detail.aiEvaluationReport();
        if (report == null || !hasText(report.failureReason())) {
            return null;
        }
        return report.failureReason();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 특허 원문 URL 생성 규칙(MAIL-10) — PDF 폴백(MAIL-12) 등에서 재사용하도록 공개한다. */
    public static String originalPatentUrl(String country, String applicationNumber, String registrationNumber) {
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

    private String valueOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static String firstNonBlank(String firstValue, String secondValue) {
        if (firstValue != null && !firstValue.isBlank()) {
            return firstValue;
        }
        if (secondValue != null && !secondValue.isBlank()) {
            return secondValue;
        }
        return null;
    }

    private PatentReviewHistoryEntity latestHistoryOrNull(String patentId) {
        return reviewHistoryRepository.findByPatentIdOrderByCreatedAtDesc(patentId).stream()
                .findFirst()
                .orElse(null);
    }

    private PatentDetailResponse applyPersistedState(PatentDetailResponse patent) {
        return applyPersistedState(patent, latestHistoryOrNull(patent.patentId()));
    }

    private PatentDetailResponse applyPersistedState(PatentDetailResponse patent, PatentReviewHistoryEntity latestOrNull) {
        if (latestOrNull != null) {
            return applyHistoryState(patent, latestOrNull);
        }
        return patent;
    }

    private PatentDetailResponse applyHistoryState(PatentDetailResponse patent, PatentReviewHistoryEntity state) {
        Recommendation baseRecommendation = state.getAiRecommendation() != null
                ? state.getAiRecommendation()
                : patent.currentRecommendation();
        AiEvaluationReportResponse aiReport = aiReportFromHistory(state, patent.aiEvaluationReport(), baseRecommendation);
        // 법무 편집이 권고를 바꿨다면 목록/상세의 현재 권고도 유효(편집 반영) 값을 따른다.
        Recommendation currentRecommendation = aiReport.recommendation() != null
                ? aiReport.recommendation()
                : baseRecommendation;
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
                        state.getFinalDecisionDecidedAt(),
                        state.getFinalDecisionDecidedBy()),
                new BusinessOpinionResponse(
                        state.getBusinessOpinionDecision(),
                        state.getBusinessOpinionReason(),
                        state.getBusinessOpinionSubmittedAt()),
                patent.inReview(),
                patent.jointApplication(), coApplicantConsentFromHistory(state));
    }

    private AiEvaluationReportResponse aiReportFromHistory(
            PatentReviewHistoryEntity state,
            AiEvaluationReportResponse fallback,
            Recommendation recommendation
    ) {
        AiEvaluationReportResponse original = originalAiReportFromHistory(state, fallback, recommendation);
        // FR-LEGAL-09: AI 원본 위에 법무 편집 오버라이드를 오버레이한 '유효 레포트'를 돌려준다.
        // 합성 결과는 조회 전용이며, persistPatentState의 동일 reportId 가드로 ai_* 컬럼에 역류하지 않는다.
        AiReportOverrides overrides = AiReportOverridesSupport.readOverrides(objectMapper, state.getAiEditOverridesJson());
        return AiReportOverridesSupport.applyOverrides(original, overrides, state);
    }

    /** AI 원본 레포트(편집 미반영). 'AI 원본 보기'와 편집 충돌 검증이 사용한다. */
    AiEvaluationReportResponse originalAiReportFromHistory(
            PatentReviewHistoryEntity state,
            AiEvaluationReportResponse fallback,
            Recommendation recommendation
    ) {
        if (state.getAiReportId() == null) {
            return withAiRecommendation(fallback, recommendation);
        }
        return AiReportOverridesSupport.withAppliedCriteria(
                new AiEvaluationReportResponse(
                        state.getAiReportId(),
                        state.getAiReportCreatedAt(),
                        recommendation,
                        state.getAiRecommendationReason(),
                        state.getAiTotalScore(),
                        state.getAiAverageScore(),
                        state.getAiFinalGrade(),
                        Boolean.TRUE.equals(state.getAiDegraded()),
                        state.getAiFailureReason(),
                        readEvaluationScores(state.getAiScoresJson()),
                        readStringList(state.getAiMissingInformationJson()),
                        state.getAiReportMarkdown(),
                        state.getAiReportMarkdownPath(),
                        // ORCH-06/AIREPORT-02: 저장된 리치 근거를 복원한다.
                        state.getAiKeyEvidence(),
                        readStringList(state.getAiJudgementGroundsJson()),
                        readStringList(state.getAiBusinessCheckRequestsJson()),
                        readSourceList(state.getAiExternalSourcesJson())),
                readCriteriaMap(state.getAiAppliedCriteriaJson()));
    }

    /**
     * @relatedFR FR-LEGAL-09
     * @description 'AI 원본 보기' — 법무 편집을 반영하지 않은 순수 AI 레포트를 조회한다.
     */
    @Transactional
    public AiEvaluationReportResponse getOriginalAiReport(String patentId) {
        PatentDetailResponse patent = findPatent(patentId);
        PatentReviewHistoryEntity state = latestHistoryOrNull(patentId);
        if (state == null) {
            return patent.aiEvaluationReport();
        }
        Recommendation recommendation = state.getAiRecommendation() != null
                ? state.getAiRecommendation()
                : patent.currentRecommendation();
        // fallback 인자는 원본 조회에서 의미가 없어 현재 detail의 레포트를 그대로 쓴다(aiReportId 없을 때만 사용됨).
        return originalAiReportFromHistory(state, patent.aiEvaluationReport(), recommendation);
    }

    private java.util.Map<String, Object> readCriteriaMap(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return null;
        }
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
                report.averageScore(),
                report.finalGrade(),
                report.degraded(),
                report.failureReason(),
                report.scores(),
                report.missingInformation(),
                report.rawMarkdown(),
                report.markdownFilePath(),
                // ORCH-06/AIREPORT-02: 권고만 바꾸고 리치 근거는 그대로 패스스루.
                report.keyEvidence(),
                report.judgementGrounds(),
                report.businessCheckRequests(),
                report.externalSources());
    }

    private void applyAiReportToHistory(PatentReviewHistoryEntity state, AiEvaluationReportResponse report) {
        // 동일 reportId의 재영속은 스킵한다. 레포트 내용은 reportId 단위로 불변이며, 조회 경로가
        // 법무 편집 오버라이드를 오버레이한 '유효 레포트'를 돌려주므로(aiReportFromHistory),
        // 이를 다시 쓰면 편집 값이 AI 원본(ai_*) 컬럼을 오염시킨다.
        if (report.reportId() != null && report.reportId().equals(state.getAiReportId())) {
            return;
        }
        state.setAiReportId(report.reportId());
        state.setAiReportCreatedAt(report.createdAt());
        state.setAiRecommendation(report.recommendation());
        state.setAiRecommendationReason(report.recommendationReason());
        state.setAiTotalScore(report.totalScore());
        state.setAiAverageScore(report.averageScore());
        state.setAiFinalGrade(report.finalGrade());
        state.setAiDegraded(report.degraded());
        state.setAiFailureReason(report.failureReason());
        state.setAiScoresJson(writeJson(report.scores()));
        state.setAiMissingInformationJson(writeJson(report.missingInformation()));
        state.setAiReportMarkdown(report.rawMarkdown());
        state.setAiReportMarkdownPath(report.markdownFilePath());
        // ORCH-06/AIREPORT-02: 리치 근거를 저장해 재조회 시 유실되지 않게 한다.
        state.setAiKeyEvidence(report.keyEvidence());
        state.setAiJudgementGroundsJson(writeJson(report.judgementGrounds()));
        state.setAiBusinessCheckRequestsJson(writeJson(report.businessCheckRequests()));
        state.setAiExternalSourcesJson(writeJson(report.externalSources()));
        // UI-008: 이 레포트에 적용된 가치평가 기준 스냅샷(없으면 null — 구 agent/시드 레포트).
        state.setAiAppliedCriteriaJson(report.appliedCriteria() == null ? null : writeJson(report.appliedCriteria()));
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

    // ORCH-06/AIREPORT-02: 외부 출처(externalSources) JSON 역직렬화.
    private List<SourceResponse> readSourceList(String value) {
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
        // ai_recommendation은 applyAiReportToHistory가 레포트와 함께 기록한다. 여기서
        // currentRecommendation(법무 편집이 반영된 유효 값)을 쓰면 AI 원본 권고가 오염된다.
        if (state.getAiReportId() == null) {
            state.setAiRecommendation(patent.currentRecommendation());
        }
        applyAiReportToHistory(state, patent.aiEvaluationReport());
        applySummaryToHistory(state, patent.summary());
        state.setBusinessOpinionDecision(patent.businessOpinionDecision());
        state.setBusinessOpinionReason(patent.businessOpinion().reason());
        state.setBusinessOpinionSubmittedAt(patent.businessOpinion().submittedAt());
        CoApplicantConsentResponse consent = patent.coApplicantConsent();
        state.setCoApplicantConsentStatus(consent == null ? null : consent.status());
        state.setCoApplicantConsentReason(consent == null ? null : consent.reason());
        state.setCoApplicantConsentDecidedBy(consent == null ? null : consent.decidedBy());
        state.setCoApplicantConsentDecidedAt(consent == null ? null : consent.decidedAt());
        state.setLegalActionResult(patent.legalActionResult());
        state.setFinalDecisionId(patent.finalDecisionRecord().decisionId());
        state.setFinalDecisionReason(patent.finalDecisionRecord().reason());
        state.setFinalDecisionDecidedAt(patent.finalDecisionRecord().decidedAt());
        state.setFinalDecisionDecidedBy(patent.finalDecisionRecord().decidedBy());
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

    private interface PatentUpdater {
        PatentDetailResponse update(PatentDetailResponse patent);
    }

}
