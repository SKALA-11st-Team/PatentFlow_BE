package com.syuuk.patentflow.settings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.settings.domain.ValuationCriteriaConfigEntity;
import com.syuuk.patentflow.settings.dto.ValuationCriteriaRequest;
import com.syuuk.patentflow.settings.dto.ValuationCriteriaResponse;
import com.syuuk.patentflow.settings.repository.ValuationCriteriaConfigRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * UI-008 가치평가 기준 설정 — 검증 규칙(가중치 합 100, 컷오프 순서, subscore 합 100)과
 * 버전 증가, 기본값 미러링(agent DEFAULT_*와 일치)을 검증한다.
 */
class ValuationCriteriaServiceTest {

    private ValuationCriteriaConfigRepository repository;
    private ValuationCriteriaService service;

    @BeforeEach
    void setUp() {
        repository = mock(ValuationCriteriaConfigRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ValuationCriteriaService(repository, new ObjectMapper());
    }

    private ValuationCriteriaRequest validRequest() {
        return new ValuationCriteriaRequest(
                Map.of("legal", 30.0, "technology", 20.0, "market", 30.0, "business_fit", 20.0),
                Map.of("A", 80.0, "B", 60.0, "C", 40.0),
                60.0,
                Map.of(
                        "legal", Map.of("right_stability", 35, "claim_protection", 40, "portfolio_defensive_value", 25),
                        "business_fit", Map.of("official_business_evidence", 30, "product_function_direct_match", 45,
                                "business_context_fit", 25)));
    }

    @Test
    void defaultsMirrorAgentHardcodedCriteria() {
        when(repository.findTopByOrderByVersionDesc()).thenReturn(Optional.empty());

        ValuationCriteriaResponse current = service.getCurrent();

        assertThat(current.isDefault()).isTrue();
        assertThat(current.config().get("axisWeights"))
                .isEqualTo(Map.of("legal", 25.0, "technology", 25.0, "market", 25.0, "business_fit", 25.0));
        assertThat(current.config().get("gradeCutoffs")).isEqualTo(Map.of("A", 80.0, "B", 60.0, "C", 40.0));
        assertThat(current.config().get("maintainThreshold")).isEqualTo(60.0);
        assertThat(current.config().get("version")).isEqualTo(0);
        // 미설정 상태에서는 agent에 config를 보내지 않는다(agent 자체 기본값 사용 — 구 agent 호환).
        assertThat(service.currentConfigForAgent()).isNull();
    }

    @Test
    void updateCreatesNextVersionAndReturnsConfig() {
        when(repository.findTopByOrderByVersionDesc())
                .thenReturn(Optional.of(new ValuationCriteriaConfigEntity(2, "{\"version\":2}", "admin",
                        java.time.OffsetDateTime.now())));

        ValuationCriteriaResponse updated = service.update(validRequest(), "legal01");

        assertThat(updated.isDefault()).isFalse();
        assertThat(updated.config().get("version")).isEqualTo(3);
        assertThat(updated.updatedBy()).isEqualTo("legal01");
        assertThat(((Map<?, ?>) updated.config().get("axisWeights")).get("legal")).isEqualTo(30.0);
    }

    @Test
    void axisWeightsMustSumTo100() {
        ValuationCriteriaRequest invalid = new ValuationCriteriaRequest(
                Map.of("legal", 30.0, "technology", 20.0, "market", 30.0, "business_fit", 30.0),
                validRequest().gradeCutoffs(), 60.0, validRequest().subscoreWeights());

        assertThatThrownBy(() -> service.update(invalid, "legal01"))
                .isInstanceOf(PatentFlowException.class)
                .extracting(e -> ((PatentFlowException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void axisWeightsMustCoverAllFourAxes() {
        ValuationCriteriaRequest invalid = new ValuationCriteriaRequest(
                Map.of("legal", 50.0, "technology", 50.0),
                validRequest().gradeCutoffs(), 60.0, validRequest().subscoreWeights());

        assertThatThrownBy(() -> service.update(invalid, "legal01"))
                .isInstanceOf(PatentFlowException.class);
    }

    @Test
    void gradeCutoffsMustBeStrictlyOrdered() {
        ValuationCriteriaRequest invalid = new ValuationCriteriaRequest(
                validRequest().axisWeights(),
                Map.of("A", 50.0, "B", 60.0, "C", 40.0),
                60.0, validRequest().subscoreWeights());

        assertThatThrownBy(() -> service.update(invalid, "legal01"))
                .isInstanceOf(PatentFlowException.class);
    }

    @Test
    void subscoreGroupMustSumTo100() {
        ValuationCriteriaRequest invalid = new ValuationCriteriaRequest(
                validRequest().axisWeights(), validRequest().gradeCutoffs(), 60.0,
                Map.of(
                        "legal", Map.of("right_stability", 50, "claim_protection", 40, "portfolio_defensive_value", 25),
                        "business_fit", Map.of("official_business_evidence", 30, "product_function_direct_match", 45,
                                "business_context_fit", 25)));

        assertThatThrownBy(() -> service.update(invalid, "legal01"))
                .isInstanceOf(PatentFlowException.class);
    }

    @Test
    void maintainThresholdMustBeWithinRange() {
        ValuationCriteriaRequest invalid = new ValuationCriteriaRequest(
                validRequest().axisWeights(), validRequest().gradeCutoffs(), 150.0, validRequest().subscoreWeights());

        assertThatThrownBy(() -> service.update(invalid, "legal01"))
                .isInstanceOf(PatentFlowException.class);
    }

    @Test
    void currentConfigForAgentReturnsLatestConfig() {
        when(repository.findTopByOrderByVersionDesc())
                .thenReturn(Optional.of(new ValuationCriteriaConfigEntity(
                        1, "{\"version\":1,\"maintainThreshold\":70.0}", "admin", java.time.OffsetDateTime.now())));

        Map<String, Object> config = service.currentConfigForAgent();

        assertThat(config).containsEntry("version", 1).containsEntry("maintainThreshold", 70.0);
    }
}
