package com.syuuk.patentflow.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.common.domain.SystemSettingsEntity;
import com.syuuk.patentflow.common.dto.ClassificationResponse;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.repository.SystemSettingsRepository;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    @Mock
    private SystemSettingsRepository repository;

    @Mock
    private PatentMetadataRepository patentMetadataRepository;

    private SystemSettingsService service;

    @BeforeEach
    void setUp() {
        // 키 미설정 SecretCipher = 평문 통과(기존 동작 유지). 암호화 경로는 SecretCipherTest에서 검증.
        service = new SystemSettingsService(
                repository, patentMetadataRepository, new com.syuuk.patentflow.common.security.SecretCipher(""));
    }

    @Test
    void getClassificationsReadsLegacyNewlineValuesAsSortedDistinctList() {
        SystemSettingsEntity saved = new SystemSettingsEntity("classification.business");
        saved.setValue("Zeta\nAlpha\nZeta\n");

        when(repository.findById("classification.business")).thenReturn(Optional.of(saved));
        when(repository.findById("classification.technology")).thenReturn(Optional.empty());

        List<ClassificationResponse> classifications = service.getClassifications();

        ClassificationResponse business = classifications.stream()
                .filter(item -> item.type().equals("BUSINESS"))
                .findFirst()
                .orElseThrow();
        assertThat(business.values()).containsExactly("Alpha", "Zeta");
    }

    // SETTINGS-11: 회차별 연장 기간 CSV 파싱 — 정상 파싱, 초과 회차는 마지막 값, 미설정은 단일값 폴백.
    @Test
    void extensionRoundsParseAndClampToLastRound() {
        SystemSettingsEntity rounds = new SystemSettingsEntity("country.extension.JP.rounds");
        rounds.setValue("12,6,24");
        when(repository.findById("country.extension.JP.rounds")).thenReturn(Optional.of(rounds));

        assertThat(service.getCountryExtensionRounds("JP")).containsExactly(12, 6, 24);
        assertThat(service.getCountryExtensionMonthsForRound("JP", 2)).isEqualTo(6);
        assertThat(service.getCountryExtensionMonthsForRound("JP", 9)).isEqualTo(24);
    }

    @Test
    void extensionRoundsFallBackToSingleValueWhenUnset() {
        SystemSettingsEntity single = new SystemSettingsEntity("country.extension.CN");
        single.setValue("18");
        when(repository.findById("country.extension.CN.rounds")).thenReturn(Optional.empty());
        when(repository.findById("country.extension.CN")).thenReturn(Optional.of(single));

        assertThat(service.getCountryExtensionRounds("CN")).isEmpty();
        assertThat(service.getCountryExtensionMonthsForRound("CN", 3)).isEqualTo(18);
    }

    @Test
    void addClassificationStoresSortedJsonArrayUnderLock() throws Exception {
        SystemSettingsEntity saved = new SystemSettingsEntity("classification.business");
        saved.setValue("[\"Zeta\"]");

        when(repository.findByIdForUpdate("classification.business")).thenReturn(Optional.of(saved));

        service.addClassification("BUSINESS", "Alpha");

        verify(repository).save(argThat(entity -> {
            try {
                List<String> values = OBJECT_MAPPER.readValue(entity.getValue(), STRING_LIST);
                return values.equals(List.of("Alpha", "Zeta"));
            } catch (Exception e) {
                return false;
            }
        }));
    }

    // be-settings-4: 사용 중인 분류 삭제는 거부 — 고아(business_area 참조) 방지.
    @Test
    void deleteClassificationRejectedWhenInUseByPatent() {
        when(patentMetadataRepository.findDistinctBusinessAreas()).thenReturn(List.of("AI", "Cloud"));

        assertThatThrownBy(() -> service.deleteClassification("BUSINESS", "AI"))
                .isInstanceOf(PatentFlowException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // be-settings-4: 사용 중이 아니면 정상 삭제(기준 목록만 갱신).
    @Test
    void deleteClassificationAllowedWhenNotInUse() {
        when(patentMetadataRepository.findDistinctBusinessAreas()).thenReturn(List.of("Cloud"));
        SystemSettingsEntity saved = new SystemSettingsEntity("classification.business");
        saved.setValue("[\"AI\",\"Cloud\"]");
        when(repository.findByIdForUpdate("classification.business")).thenReturn(Optional.of(saved));

        ClassificationResponse response = service.deleteClassification("BUSINESS", "AI");

        assertThat(response.values()).containsExactly("Cloud");
        verify(repository).save(org.mockito.ArgumentMatchers.any());
    }

    // be-settings-5: 회신 기한 일수(days) 상한(31)을 서비스 계층에서도 검증.
    @Test
    void updateResponseDeadlineRejectsDaysAbove31() {
        assertThatThrownBy(() -> service.updateResponseDeadline(0, 32))
                .isInstanceOf(PatentFlowException.class);
    }

    @Test
    void updateResponseDeadlineAcceptsDaysAtUpperBound() {
        service.updateResponseDeadline(0, 31);

        verify(repository).save(argThat(entity ->
                "review.response.deadline.days".equals(entity.getKey()) && "31".equals(entity.getValue())));
    }
}
