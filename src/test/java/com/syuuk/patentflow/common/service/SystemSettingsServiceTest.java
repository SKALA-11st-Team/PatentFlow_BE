package com.syuuk.patentflow.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.common.domain.SystemSettingsEntity;
import com.syuuk.patentflow.common.dto.ClassificationResponse;
import com.syuuk.patentflow.common.repository.SystemSettingsRepository;
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

    private SystemSettingsService service;

    @BeforeEach
    void setUp() {
        service = new SystemSettingsService(repository);
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
}
