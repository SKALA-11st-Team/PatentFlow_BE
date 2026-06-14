package com.syuuk.patentflow.settings.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.settings.domain.ValuationCriteriaConfigEntity;
import com.syuuk.patentflow.settings.dto.ValuationCriteriaRequest;
import com.syuuk.patentflow.settings.dto.ValuationCriteriaResponse;
import com.syuuk.patentflow.settings.dto.ValuationCriteriaVersionResponse;
import com.syuuk.patentflow.settings.repository.ValuationCriteriaConfigRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 가치평가 기준(UI-008 '평가 기준 구성')의 버전 관리 서비스.
 *
 * 기본값은 agent의 하드코딩 기준(schemas/valuation.py DEFAULT_*)을 그대로 미러링한다 —
 * 설정이 없으면 agent도 같은 기본값으로 평가하므로 양쪽이 항상 일치한다.
 * 기준 변경은 이후 생성되는 레포트부터 적용되며 소급 재채점하지 않는다(레포트별
 * appliedCriteria 스냅샷으로 추적).
 */
@Service
public class ValuationCriteriaService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<String> AXES = Set.of("legal", "technology", "market", "business_fit");
    private static final Map<String, Set<String>> SUBSCORE_KEYS = Map.of(
            "legal", Set.of("right_stability", "claim_protection", "portfolio_defensive_value"),
            "business_fit", Set.of("official_business_evidence", "product_function_direct_match", "business_context_fit"));
    private static final double SUM_EPSILON = 0.001;

    private final ValuationCriteriaConfigRepository repository;
    private final ObjectMapper objectMapper;

    public ValuationCriteriaService(ValuationCriteriaConfigRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** agent 기본값(schemas/valuation.py DEFAULT_*)과 동일해야 한다. */
    static Map<String, Object> defaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("version", 0);
        config.put("axisWeights", Map.of("legal", 25.0, "technology", 25.0, "market", 25.0, "business_fit", 25.0));
        config.put("gradeCutoffs", Map.of("A", 80.0, "B", 60.0, "C", 40.0));
        config.put("maintainThreshold", 60.0);
        Map<String, Object> subscores = new LinkedHashMap<>();
        subscores.put("legal", Map.of("right_stability", 40, "claim_protection", 40, "portfolio_defensive_value", 20));
        subscores.put("business_fit",
                Map.of("official_business_evidence", 30, "product_function_direct_match", 45, "business_context_fit", 25));
        config.put("subscoreWeights", subscores);
        return config;
    }

    /**
     * @relatedFR FR-LEGAL-09
     * @relatedUI UI-008
     * @description 현재 활성 가치평가 기준을 조회한다(미설정 시 기본값 + isDefault=true).
     */
    @Transactional(readOnly = true)
    public ValuationCriteriaResponse getCurrent() {
        return repository.findTopByOrderByVersionDesc()
                .map(entity -> new ValuationCriteriaResponse(
                        readConfig(entity.getConfigJson()), false, entity.getCreatedBy(), entity.getCreatedAt()))
                .orElseGet(() -> new ValuationCriteriaResponse(defaultConfig(), true, null, null));
    }

    /**
     * @relatedUI UI-008
     * @description 기준을 검증 후 새 버전으로 저장한다. 이후 생성 레포트부터 적용된다.
     */
    @Transactional
    public ValuationCriteriaResponse update(ValuationCriteriaRequest request, String actor) {
        validate(request);
        int nextVersion = repository.findTopByOrderByVersionDesc()
                .map(entity -> entity.getVersion() + 1)
                .orElse(1);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("version", nextVersion);
        config.put("axisWeights", request.axisWeights());
        config.put("gradeCutoffs", request.gradeCutoffs());
        config.put("maintainThreshold", request.maintainThreshold());
        config.put("subscoreWeights", request.subscoreWeights());
        OffsetDateTime now = OffsetDateTime.now(KST);
        try {
            ValuationCriteriaConfigEntity saved = repository.save(
                    new ValuationCriteriaConfigEntity(nextVersion, writeConfig(config), actor, now));
            return new ValuationCriteriaResponse(readConfig(saved.getConfigJson()), false, actor, now);
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            // 동시 저장으로 같은 버전을 계산한 경우(version unique 충돌) — 500 대신 409로 안내.
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS,
                    "다른 관리자가 동시에 기준을 저장했습니다. 새로고침 후 다시 시도해주세요.");
        }
    }

    /**
     * @relatedUI UI-008
     * @description 기준 버전 이력(최신순)을 조회한다.
     */
    @Transactional(readOnly = true)
    public List<ValuationCriteriaVersionResponse> history() {
        return repository.findAllByOrderByVersionDesc().stream()
                .map(entity -> new ValuationCriteriaVersionResponse(
                        entity.getVersion(), entity.getCreatedBy(), entity.getCreatedAt(),
                        readConfig(entity.getConfigJson())))
                .toList();
    }

    /**
     * agent evaluate 요청에 실을 현재 설정(계약 C1 형태). 한 번도 설정된 적이 없으면 null을
     * 돌려 agent가 자체 기본값으로 평가하게 한다(구 agent 호환 — 필드 자체를 생략).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> currentConfigForAgent() {
        return repository.findTopByOrderByVersionDesc()
                .map(entity -> readConfig(entity.getConfigJson()))
                .orElse(null);
    }

    private void validate(ValuationCriteriaRequest request) {
        Map<String, Double> weights = request.axisWeights();
        if (!weights.keySet().equals(AXES)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "축 가중치는 legal/technology/market/business_fit 4개 축을 모두 포함해야 합니다.");
        }
        double weightSum = 0;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "축 가중치는 0보다 커야 합니다: " + entry.getKey());
            }
            weightSum += entry.getValue();
        }
        if (Math.abs(weightSum - 100.0) > SUM_EPSILON) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "축 가중치 합계는 100이어야 합니다. 현재: " + weightSum);
        }

        Double cutoffA = request.gradeCutoffs().get("A");
        Double cutoffB = request.gradeCutoffs().get("B");
        Double cutoffC = request.gradeCutoffs().get("C");
        if (cutoffA == null || cutoffB == null || cutoffC == null
                || !(100 >= cutoffA && cutoffA > cutoffB && cutoffB > cutoffC && cutoffC >= 0)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "등급 컷오프는 100 ≥ A > B > C ≥ 0 순서를 지켜야 합니다.");
        }

        if (request.maintainThreshold() < 0 || request.maintainThreshold() > 100) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "유지 권고 임계값은 0~100 사이여야 합니다.");
        }

        for (Map.Entry<String, Set<String>> group : SUBSCORE_KEYS.entrySet()) {
            Map<String, Integer> configured = request.subscoreWeights().get(group.getKey());
            if (configured == null || !configured.keySet().equals(group.getValue())) {
                throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "subscore 배점(%s)은 정의된 항목을 모두 포함해야 합니다.".formatted(group.getKey()));
            }
            int sum = 0;
            for (Map.Entry<String, Integer> entry : configured.entrySet()) {
                if (entry.getValue() == null || entry.getValue() < 0) {
                    throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                            "subscore 배점은 0 이상이어야 합니다: " + entry.getKey());
                }
                sum += entry.getValue();
            }
            if (sum != 100) {
                throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "subscore 배점(%s) 합계는 100이어야 합니다. 현재: %d".formatted(group.getKey(), sum));
            }
        }
    }

    private Map<String, Object> readConfig(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("가치평가 기준 JSON을 읽을 수 없습니다.", exception);
        }
    }

    private String writeConfig(Map<String, Object> config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception exception) {
            throw new IllegalStateException("가치평가 기준 JSON을 저장할 수 없습니다.", exception);
        }
    }
}
