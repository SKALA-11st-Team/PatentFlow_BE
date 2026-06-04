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
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnualFeeScheduleManagementService {

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

    public List<AnnualFeeScheduleItemResponse> getSchedule(String country) {
        String normalizedCountry = country == null || country.isBlank() || "ALL".equalsIgnoreCase(country.trim()) ? null : country.trim().toUpperCase();
        List<PatentMetadataEntity> patents = patentMetadataRepository.findByCountryOrderByFeeDueDateAndManagementNumber(normalizedCountry);
        
        // [최적화] 각 특허마다 연차료 조정 이력을 조회하는 N+1 쿼리를 방지하기 위해,
        // 전체 조정 이력을 한 번에 가져와서 메모리에서 특허 ID를 기준으로 그룹핑(In-memory Join) 처리
        java.util.Map<String, List<AnnualFeeAdjustmentEntity>> adjustmentsByPatent = adjustmentRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(AnnualFeeAdjustmentEntity::getAdjustedAt).reversed())
                .collect(java.util.stream.Collectors.groupingBy(AnnualFeeAdjustmentEntity::getPatentId));

        return patents.stream()
                .map(p -> toResponseWithHistory(p, adjustmentsByPatent.getOrDefault(p.getPatentId(), List.of())))
                .toList();
    }

    @Transactional
    public AnnualFeeScheduleItemResponse adjustSchedule(String patentId, AnnualFeeScheduleAdjustmentRequest request) {
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
                request.adjustedBy() == null || request.adjustedBy().isBlank() ? "관리자" : request.adjustedBy().trim()));

        return toResponseWithHistory(patent, adjustmentRepository.findByPatentIdOrderByAdjustedAtDesc(patent.getPatentId()));
    }

    private AnnualFeeScheduleItemResponse toResponseWithHistory(PatentMetadataEntity patent, List<AnnualFeeAdjustmentEntity> adjustmentEntities) {
        List<AnnualFeeAdjustmentHistoryResponse> history = adjustmentEntities.stream()
                .map(this::toHistoryResponse)
                .toList();
        AnnualFeeAdjustmentHistoryResponse latestAdjustment = history.isEmpty() ? null : history.get(0);
        LocalDate baseDate = patent.getApplicationDate() != null ? patent.getApplicationDate() : patent.getRegistrationDate();
        LocalDate calculatedDueDate = annualFeeScheduleService.calculateNextDueDate(
                patent.getCountry(),
                patent.getApplicationDate(),
                patent.getRegistrationDate(),
                patent.getExpectedExpirationDate());

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
                patent.getFeeDueDate() != null ? patent.getFeeDueDate() : calculatedDueDate,
                latestAdjustment == null ? null : latestAdjustment.adjustedDueDate(),
                latestAdjustment == null ? null : latestAdjustment.reason(),
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
