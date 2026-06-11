package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.patent.domain.AnnualFeeAdjustmentEntity;
import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import com.syuuk.patentflow.patent.domain.PatentReviewHistoryEntity;
import com.syuuk.patentflow.patent.dto.AnnualFeeAdjustmentHistoryResponse;
import com.syuuk.patentflow.patent.dto.AnnualFeeScheduleAdjustmentRequest;
import com.syuuk.patentflow.patent.dto.AnnualFeeScheduleItemResponse;
import com.syuuk.patentflow.patent.repository.AnnualFeeAdjustmentRepository;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnualFeeScheduleManagementService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AnnualFeeAdjustmentRepository adjustmentRepository;
    private final AnnualFeeScheduleService annualFeeScheduleService;
    private final PatentMetadataRepository patentMetadataRepository;
    private final PatentReviewHistoryRepository reviewHistoryRepository;

    public AnnualFeeScheduleManagementService(
            AnnualFeeAdjustmentRepository adjustmentRepository,
            AnnualFeeScheduleService annualFeeScheduleService,
            PatentMetadataRepository patentMetadataRepository,
            PatentReviewHistoryRepository reviewHistoryRepository
    ) {
        this.adjustmentRepository = adjustmentRepository;
        this.annualFeeScheduleService = annualFeeScheduleService;
        this.patentMetadataRepository = patentMetadataRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
    }

    @Transactional(readOnly = true)
    public List<AnnualFeeScheduleItemResponse> getSchedule(String country) {
        String normalizedCountry = country == null ? null : country.trim().toUpperCase();
        List<PatentMetadataEntity> patents = normalizedCountry == null || normalizedCountry.isBlank() || "ALL".equals(normalizedCountry)
                ? patentMetadataRepository.findAllByOrderByFeeDueDateAscManagementNumberAsc()
                : patentMetadataRepository.findByCountryIgnoreCaseOrderByFeeDueDateAscManagementNumberAsc(normalizedCountry);
        Map<String, List<AnnualFeeAdjustmentHistoryResponse>> historiesByPatentId = findAdjustmentHistoriesByPatentId(patents);
        return patents.stream()
                .map(patent -> toResponse(patent, historiesByPatentId.getOrDefault(patent.getPatentId(), List.of())))
                .toList();
    }

    @Transactional
    public AnnualFeeScheduleItemResponse adjustSchedule(
            String patentId,
            AnnualFeeScheduleAdjustmentRequest request,
            String adjustedBy
    ) {
        PatentMetadataEntity patent = patentMetadataRepository.findById(patentId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.PATENT_NOT_FOUND));
        if (request.adjustedDueDate() == null) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "조정 납부일을 입력해 주세요.");
        }
        if (patent.getExpectedExpirationDate() != null && request.adjustedDueDate().isAfter(patent.getExpectedExpirationDate())) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "조정 납부일은 예상 소멸일 이후로 설정할 수 없습니다.");
        }

        LocalDate previousDueDate = patent.getFeeDueDate();
        patent.setFeeDueDate(request.adjustedDueDate());
        patentMetadataRepository.save(patent);

        List<PatentReviewHistoryEntity> histories = reviewHistoryRepository.findByPatentIdOrderByCreatedAtDesc(patentId);
        if (!histories.isEmpty()) {
            PatentReviewHistoryEntity latestHistory = histories.get(0);
            latestHistory.setAnnualFeeDueDate(request.adjustedDueDate());
            reviewHistoryRepository.save(latestHistory);
        }

        adjustmentRepository.save(new AnnualFeeAdjustmentEntity(
                "AF-ADJ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                patentId,
                previousDueDate,
                request.adjustedDueDate(),
                request.reason(),
                adjustedBy == null || adjustedBy.isBlank() ? "관리자" : adjustedBy.trim()));

        return toResponse(patent, adjustmentRepository.findByPatentIdOrderByAdjustedAtDesc(patent.getPatentId()).stream()
                .map(this::toHistoryResponse)
                .toList());
    }

    /**
     * FEE-05: 저장된 납부일이 과거인 특허를 국가 연장 개월 단위로 굴려 실제 저장값까지 전진시킨다.
     * 조회 시점 effective 표기와 달리 이 경로는 영속화한다(관리자 수동/배치 트리거). 전진된 특허 수를 반환.
     */
    @Transactional
    public int recomputeOverdueSchedules() {
        LocalDate today = LocalDate.now(KST);
        int updated = 0;
        for (PatentMetadataEntity patent : patentMetadataRepository.findAll()) {
            LocalDate stored = patent.getFeeDueDate();
            if (stored == null || !stored.isBefore(today)) {
                continue;
            }
            LocalDate rolled = annualFeeScheduleService.rollForwardToFuture(
                    patent.getCountry(), stored, patent.getExpectedExpirationDate(), today);
            if (rolled != null && rolled.isAfter(stored)) {
                patent.setFeeDueDate(rolled);
                patentMetadataRepository.save(patent);
                updated++;
            }
        }
        return updated;
    }

    private Map<String, List<AnnualFeeAdjustmentHistoryResponse>> findAdjustmentHistoriesByPatentId(List<PatentMetadataEntity> patents) {
        if (patents.isEmpty()) {
            return Map.of();
        }
        List<String> patentIds = patents.stream()
                .map(PatentMetadataEntity::getPatentId)
                .toList();
        Map<String, List<AnnualFeeAdjustmentHistoryResponse>> historiesByPatentId = new LinkedHashMap<>();
        adjustmentRepository.findByPatentIdInOrderByPatentIdAscAdjustedAtDesc(patentIds).forEach(entity -> {
            historiesByPatentId.computeIfAbsent(entity.getPatentId(), ignored -> new ArrayList<>())
                    .add(toHistoryResponse(entity));
        });
        historiesByPatentId.replaceAll((ignored, histories) -> histories.stream()
                .sorted(Comparator.comparing(AnnualFeeAdjustmentHistoryResponse::adjustedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList());
        return Collections.unmodifiableMap(historiesByPatentId);
    }

    private AnnualFeeScheduleItemResponse toResponse(PatentMetadataEntity patent, List<AnnualFeeAdjustmentHistoryResponse> history) {
        AnnualFeeAdjustmentHistoryResponse latestAdjustment = history.isEmpty() ? null : history.get(0);
        LocalDate baseDate = annualFeeScheduleService.annualFeeBaseDate(
                patent.getCountry(), patent.getApplicationDate(), patent.getRegistrationDate());
        LocalDate calculatedDueDate = annualFeeScheduleService.calculateNextDueDate(
                patent.getCountry(),
                patent.getApplicationDate(),
                patent.getRegistrationDate(),
                patent.getExpectedExpirationDate());
        // FEE-05: 저장값(stored)이 과거이면 조회 시점에 미래로 굴린 값을 effective로 노출(저장은 불변).
        LocalDate effectiveDueDate = patent.getFeeDueDate() != null
                ? annualFeeScheduleService.rollForwardToFuture(
                        patent.getCountry(), patent.getFeeDueDate(), patent.getExpectedExpirationDate())
                : calculatedDueDate;

        return new AnnualFeeScheduleItemResponse(
                patent.getPatentId(),
                patent.getManagementNumber(),
                patent.getTitle(),
                patent.getCountry(),
                "KR".equalsIgnoreCase(patent.getCountry()),
                patent.getApplicationDate(),
                patent.getRegistrationDate(),
                patent.getExpectedExpirationDate(),
                baseDate,
                calculatedDueDate,
                patent.getFeeDueDate(),
                effectiveDueDate,
                effectiveDueDate,
                latestAdjustment == null ? null : latestAdjustment.adjustedDueDate(),
                latestAdjustment == null ? null : latestAdjustment.reason(),
                annualFeeScheduleService.getCountryExtensionMonths(patent.getCountry()),
                annualFeeScheduleService.annualFeeBasis(patent.getCountry()),
                annualFeeScheduleService.paymentRuleLabel(patent.getCountry()),
                history);
    }

    private AnnualFeeAdjustmentHistoryResponse toHistoryResponse(AnnualFeeAdjustmentEntity entity) {
        return new AnnualFeeAdjustmentHistoryResponse(
                entity.getAdjustmentId(),
                entity.getPreviousDueDate(),
                entity.getAdjustedDueDate(),
                entity.getReason(),
                entity.getAdjustedBy(),
                entity.getAdjustedAt());
    }
}
