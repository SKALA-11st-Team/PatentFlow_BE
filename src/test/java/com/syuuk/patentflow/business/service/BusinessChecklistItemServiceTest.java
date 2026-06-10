package com.syuuk.patentflow.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.syuuk.patentflow.business.domain.BusinessChecklistItemEntity;
import com.syuuk.patentflow.business.dto.BusinessChecklistItemRequest;
import com.syuuk.patentflow.business.dto.BusinessChecklistItemResponse;
import com.syuuk.patentflow.business.repository.BusinessChecklistItemRepository;
import com.syuuk.patentflow.common.error.PatentFlowException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 사업부 체크리스트 항목의 DB 관리(리걸팀 편집) — 기본 항목 시드, 수정/추가/삭제 가드를 검증한다.
 */
class BusinessChecklistItemServiceTest {

    private BusinessChecklistItemRepository repository;
    private BusinessChecklistItemService service;

    @BeforeEach
    void setUp() {
        repository = mock(BusinessChecklistItemRepository.class);
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new BusinessChecklistItemService(repository);
    }

    @Test
    void seedsLegacyHardcodedItemsWhenEmpty() {
        when(repository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());

        List<BusinessChecklistItemResponse> items = service.getItems();

        // 시드 ID가 과거 제출 이력의 itemId와 동일해 기존 배포와 무중단 호환된다.
        assertThat(items).extracting(BusinessChecklistItemResponse::id)
                .containsExactly("TECH_COMPLETENESS", "TECH_ORIGINALITY", "MARKETABILITY", "EXPECTED_EFFECT");
        assertThat(items.get(0).options()).hasSize(4);
        assertThat(items.get(0).options().get(0).score()).isEqualTo(4);
    }

    @Test
    void updateItemChangesLabels() {
        BusinessChecklistItemEntity entity = BusinessChecklistItemService.defaultItems().get(0);
        when(repository.count()).thenReturn(4L);
        when(repository.findById("TECH_COMPLETENESS")).thenReturn(java.util.Optional.of(entity));

        BusinessChecklistItemResponse updated = service.updateItem("TECH_COMPLETENESS",
                new BusinessChecklistItemRequest(
                        "기술적 가치", "기술 성숙도", "설명 수정", "라벨4", "라벨3", "라벨2", "라벨1"));

        assertThat(updated.title()).isEqualTo("기술 성숙도");
        assertThat(updated.options().get(3).label()).isEqualTo("라벨1");
    }

    @Test
    void createItemAssignsGeneratedIdAndNextSortOrder() {
        when(repository.count()).thenReturn(4L);
        when(repository.findAllByOrderBySortOrderAsc()).thenReturn(BusinessChecklistItemService.defaultItems());

        BusinessChecklistItemResponse created = service.createItem(new BusinessChecklistItemRequest(
                "전략적 가치", "표준 연관성", null, "표준 필수", "표준 관련", "간접 관련", "무관"));

        assertThat(created.id()).startsWith("CHK-");
        ArgumentCaptor<BusinessChecklistItemEntity> captor = ArgumentCaptor.forClass(BusinessChecklistItemEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSortOrder()).isEqualTo(5);
    }

    @Test
    void deleteRejectsUnknownAndLastRemainingItem() {
        when(repository.count()).thenReturn(4L);
        when(repository.existsById("UNKNOWN")).thenReturn(false);
        assertThatThrownBy(() -> service.deleteItem("UNKNOWN")).isInstanceOf(PatentFlowException.class);

        when(repository.count()).thenReturn(1L);
        when(repository.existsById("TECH_COMPLETENESS")).thenReturn(true);
        assertThatThrownBy(() -> service.deleteItem("TECH_COMPLETENESS"))
                .isInstanceOf(PatentFlowException.class)
                .hasMessageContaining("최소 1개");
    }
}
