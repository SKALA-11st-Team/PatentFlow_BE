/**
 * @author 유건욱
 * @date 2026-06-11
 */
package com.syuuk.patentflow.business.service;

import com.syuuk.patentflow.business.domain.BusinessChecklistItemEntity;
import com.syuuk.patentflow.business.dto.BusinessChecklistItemRequest;
import com.syuuk.patentflow.business.dto.BusinessChecklistItemResponse;
import com.syuuk.patentflow.business.dto.BusinessChecklistScoreOptionResponse;
import com.syuuk.patentflow.business.repository.BusinessChecklistItemRepository;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사업부 평가 체크리스트 항목의 DB 관리(리걸팀 편집 가능).
 *
 * 최초 기동 시 기존 하드코딩 항목 4건을 시드한다(기존 배포 무중단 호환 — 시드 ID가
 * 과거 제출 이력의 itemId와 동일해 이력 표시가 그대로 유지된다).
 * 항목 변경은 이후 제출부터 적용되며 과거 제출은 JSON 스냅샷이라 영향이 없다.
 */
@Service
public class BusinessChecklistItemService {

    private final BusinessChecklistItemRepository repository;

    public BusinessChecklistItemService(BusinessChecklistItemRepository repository) {
        this.repository = repository;
    }

    /** 기존 BusinessFixtureService 하드코딩 항목과 동일한 시드 기본값. */
    static List<BusinessChecklistItemEntity> defaultItems() {
        return List.of(
                new BusinessChecklistItemEntity(
                        "TECH_COMPLETENESS", "기술적 가치", "기술완성도",
                        "회사가 특허 관련 기술을 얼마나 구현해 놓은 상태인지 평가",
                        "판매 가능한 수준으로 개발 완료",
                        "테스트용 제품 개발 완료",
                        "테스트용 제품 개발 진행 중",
                        "아이디어 상태",
                        1),
                new BusinessChecklistItemEntity(
                        "TECH_ORIGINALITY", "기술적 가치", "기술 독창성",
                        "기존 기술 대비 얼마나 뛰어난 기술인지 평가",
                        "타사 대비 독창적이고 최고 수준",
                        "타사와 유사하거나 약간 개량",
                        "동일 기능이나 기술 수준은 낮음",
                        "종래기술의 단순 조합 수준",
                        2),
                new BusinessChecklistItemEntity(
                        "MARKETABILITY", "경제적 가치", "시장성",
                        "국내 및 해외 경쟁사가 유사 분야의 사업을 진행할 가능성 평가",
                        "국내외 경쟁사 사업 진행 가능성 높음",
                        "국내 경쟁사 사업 진행 가능성 높음",
                        "당사만 관련 사업 진행",
                        "관련 사업 진행 회사 없음",
                        3),
                new BusinessChecklistItemEntity(
                        "EXPECTED_EFFECT", "경제적 가치", "기대효과",
                        "기술보호, 수익창출, 비용절감에 기여하는 정도 평가",
                        "기술보호, 수익창출, 비용절감 모두 기여",
                        "세 가지 중 두 가지에 기여",
                        "세 가지 중 한 가지에 기여",
                        "특허 기여도 없음",
                        4));
    }

    /**
     * @relatedFR FR-BUS-01
     * @relatedUI UI-LEGAL-04, UI-BUS-03
     * @description 체크리스트 항목 정의를 조회한다(비어 있으면 기본 항목을 시드).
     */
    @Transactional
    public List<BusinessChecklistItemResponse> getItems() {
        List<BusinessChecklistItemEntity> items = repository.findAllByOrderBySortOrderAsc();
        if (items.isEmpty()) {
            items = repository.saveAll(defaultItems());
        }
        return items.stream().map(BusinessChecklistItemService::toResponse).toList();
    }

    /**
     * @relatedUI UI-LEGAL-07
     * @description 리걸팀이 체크리스트 항목을 추가한다(이후 제출부터 적용).
     */
    @Transactional
    public BusinessChecklistItemResponse createItem(BusinessChecklistItemRequest request) {
        ensureSeeded();
        int nextOrder = repository.findAllByOrderBySortOrderAsc().stream()
                .mapToInt(BusinessChecklistItemEntity::getSortOrder)
                .max()
                .orElse(0) + 1;
        BusinessChecklistItemEntity saved = repository.save(new BusinessChecklistItemEntity(
                "CHK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                request.category().trim(), request.title().trim(), trimOrNull(request.description()),
                request.score4Label().trim(), request.score3Label().trim(),
                request.score2Label().trim(), request.score1Label().trim(),
                nextOrder));
        return toResponse(saved);
    }

    /**
     * @relatedUI UI-LEGAL-07
     * @description 리걸팀이 체크리스트 항목(제목/설명/점수 라벨)을 수정한다.
     */
    @Transactional
    public BusinessChecklistItemResponse updateItem(String itemId, BusinessChecklistItemRequest request) {
        ensureSeeded();
        BusinessChecklistItemEntity item = repository.findById(itemId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "존재하지 않는 체크리스트 항목입니다: " + itemId));
        item.setCategory(request.category().trim());
        item.setTitle(request.title().trim());
        item.setDescription(trimOrNull(request.description()));
        item.setScore4Label(request.score4Label().trim());
        item.setScore3Label(request.score3Label().trim());
        item.setScore2Label(request.score2Label().trim());
        item.setScore1Label(request.score1Label().trim());
        return toResponse(repository.save(item));
    }

    /**
     * @relatedUI UI-LEGAL-07
     * @description 리걸팀이 체크리스트 항목을 삭제한다. 과거 제출 이력(JSON 스냅샷)은 보존된다.
     */
    @Transactional
    public void deleteItem(String itemId) {
        ensureSeeded();
        if (!repository.existsById(itemId)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "존재하지 않는 체크리스트 항목입니다: " + itemId);
        }
        if (repository.count() <= 1) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "체크리스트 항목은 최소 1개가 있어야 합니다.");
        }
        repository.deleteById(itemId);
    }

    private void ensureSeeded() {
        if (repository.count() == 0) {
            repository.saveAll(defaultItems());
        }
    }

    private static String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BusinessChecklistItemResponse toResponse(BusinessChecklistItemEntity entity) {
        return new BusinessChecklistItemResponse(
                entity.getId(),
                entity.getCategory(),
                entity.getTitle(),
                entity.getDescription(),
                List.of(
                        new BusinessChecklistScoreOptionResponse(4, entity.getScore4Label()),
                        new BusinessChecklistScoreOptionResponse(3, entity.getScore3Label()),
                        new BusinessChecklistScoreOptionResponse(2, entity.getScore2Label()),
                        new BusinessChecklistScoreOptionResponse(1, entity.getScore1Label())));
    }
}
