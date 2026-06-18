/**
 * @author 유건욱
 * @date 2026-05-22
 */
package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.mailing.service.MailingService;
import com.syuuk.patentflow.patent.domain.AnnualFeeAdjustmentEntity;
import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import com.syuuk.patentflow.patent.domain.PatentReviewHistoryEntity;
import com.syuuk.patentflow.patent.dto.AnnualFeeAdjustmentHistoryResponse;
import com.syuuk.patentflow.patent.dto.AnnualFeeScheduleAdjustmentRequest;
import com.syuuk.patentflow.patent.dto.AnnualFeeScheduleItemResponse;
import com.syuuk.patentflow.patent.dto.PatentFeeScheduleResponse;
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
    private final SystemSettingsService systemSettingsService;
    private final MailingService mailingService;

    public AnnualFeeScheduleManagementService(
            AnnualFeeAdjustmentRepository adjustmentRepository,
            AnnualFeeScheduleService annualFeeScheduleService,
            PatentMetadataRepository patentMetadataRepository,
            PatentReviewHistoryRepository reviewHistoryRepository,
            SystemSettingsService systemSettingsService,
            MailingService mailingService
    ) {
        this.adjustmentRepository = adjustmentRepository;
        this.annualFeeScheduleService = annualFeeScheduleService;
        this.patentMetadataRepository = patentMetadataRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.systemSettingsService = systemSettingsService;
        this.mailingService = mailingService;
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

    /**
     * @relatedFR FR-LEGAL-24
     * @relatedUI UI-LEGAL-04
     * FEE-06: 특허 상세의 연차료 일정 — 국가 규칙 기반 도래일 목록과 고지(메일) 발송 예정일,
     * 수신처(담당 부서 주 수신자/CC)를 한 번에 내려 FE의 규칙 중복 계산을 없앤다.
     */
    @Transactional(readOnly = true)
    public PatentFeeScheduleResponse getPatentFeeSchedule(String patentId) {
        PatentMetadataEntity patent = patentMetadataRepository.findById(patentId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.PATENT_NOT_FOUND));
        CountryAnnualFeeRule rule = annualFeeScheduleService.ruleFor(patent.getCountry());
        LocalDate basisDate = annualFeeScheduleService.annualFeeBaseDate(
                patent.getCountry(), patent.getApplicationDate(), patent.getRegistrationDate());
        LocalDate calculatedDueDate = annualFeeScheduleService.calculateNextDueDate(
                patent.getCountry(),
                patent.getApplicationDate(),
                patent.getRegistrationDate(),
                patent.getExpectedExpirationDate());
        // FEE-05: 저장값이 있으면(조정 포함) 조회 시점에 미래로 굴린 값이 effective 다음 도래일.
        LocalDate effectiveDueDate = patent.getFeeDueDate() != null
                ? annualFeeScheduleService.rollForwardToFuture(
                        patent.getCountry(), patent.getFeeDueDate(), patent.getExpectedExpirationDate())
                : calculatedDueDate;
        List<AnnualFeeAdjustmentEntity> adjustments =
                adjustmentRepository.findByPatentIdOrderByAdjustedAtDesc(patentId);
        boolean nextDueAdjusted = !adjustments.isEmpty()
                && adjustments.get(0).getAdjustedDueDate() != null
                && adjustments.get(0).getAdjustedDueDate().equals(effectiveDueDate);

        int mailLeadMonths = systemSettingsService.getMailLeadMonths();
        return new PatentFeeScheduleResponse(
                patent.getPatentId(),
                patent.getCountry(),
                rule.basis(),
                basisDate,
                rule.label(),
                rule.initialLumpYears(),
                mailLeadMonths,
                resolveRecipient(patentId),
                annualFeeScheduleService.buildScheduleEntries(
                        patent.getCountry(),
                        patent.getApplicationDate(),
                        patent.getRegistrationDate(),
                        patent.getExpectedExpirationDate(),
                        effectiveDueDate,
                        nextDueAdjusted,
                        mailLeadMonths,
                        LocalDate.now(KST)));
    }

    /** 담당 부서의 메일 수신처 — FR-LEGAL-12와 동일한 users 파생 규칙을 재사용한다. 부서 미배정이면 null. */
    private PatentFeeScheduleResponse.FeeScheduleRecipient resolveRecipient(String patentId) {
        List<PatentReviewHistoryEntity> histories = reviewHistoryRepository.findByPatentIdOrderByCreatedAtDesc(patentId);
        String departmentId = histories.isEmpty() ? null : histories.get(0).getDepartmentId();
        if (departmentId == null || departmentId.isBlank()) {
            return null;
        }
        return mailingService.getRecipientMappings(departmentId).stream()
                .findFirst()
                .map(mapping -> new PatentFeeScheduleResponse.FeeScheduleRecipient(
                        mapping.departmentId(),
                        mapping.departmentName(),
                        mapping.managerName(),
                        mapping.managerEmail(),
                        mapping.ccEmails()))
                .orElse(null);
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
        CountryAnnualFeeRule rule = annualFeeScheduleService.ruleFor(patent.getCountry());
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
                // be-annualfee-3: 도래일 계산/roll-forward와 동일한 국가 규칙 주기를 노출해 응답 내 일관성을 맞춘다.
                // US(고정 윈도우)는 48, KR·기타는 규칙 주기(기본 12, 설정 오버라이드 반영)를 그대로 쓴다.
                rule.cycleMonths(),
                rule.basis(),
                rule.label(),
                rule.initialLumpYears(),
                annualFeeScheduleService.annuityYearNumber(baseDate, effectiveDueDate),
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
