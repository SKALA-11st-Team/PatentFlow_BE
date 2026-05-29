package com.syuuk.patentflow.settings.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.patent.dto.PatentListItemResponse;
import com.syuuk.patentflow.patent.service.PatentReviewService;
import com.syuuk.patentflow.settings.domain.QuarterSettingEntity;
import com.syuuk.patentflow.settings.dto.QuarterActivateResponse;
import com.syuuk.patentflow.settings.dto.QuarterSettingRequest;
import com.syuuk.patentflow.settings.dto.QuarterSettingResponse;
import com.syuuk.patentflow.settings.repository.QuarterSettingRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final QuarterSettingRepository quarterSettingRepository;
    private final PatentReviewService patentReviewService;
    private final SystemSettingsService systemSettingsService;

    public SettingsService(QuarterSettingRepository quarterSettingRepository,
            PatentReviewService patentReviewService,
            SystemSettingsService systemSettingsService) {
        this.quarterSettingRepository = quarterSettingRepository;
        this.patentReviewService = patentReviewService;
        this.systemSettingsService = systemSettingsService;
        seedDefaultQuartersIfNeeded();
    }

    @Transactional(readOnly = true)
    public List<QuarterSettingResponse> getQuarterSettings(int year) {
        return quarterSettingRepository.findByYearOrderByQuarterNumber(year).stream()
                .map(q -> toResponse(q, countTargetPatents(q)))
                .toList();
    }

    @Transactional
    public QuarterSettingResponse updateQuarterSetting(String quarterKey, QuarterSettingRequest request) {
        QuarterSettingEntity quarter = findQuarter(quarterKey);
        if (request.startDate() != null || request.endDate() != null) {
            if (quarter.isActivated()) {
                throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS, "이미 활성화된 분기는 납부 기간을 수정할 수 없습니다.");
            }
            LocalDate start = request.startDate() != null ? request.startDate() : quarter.getStartDate();
            LocalDate end = request.endDate() != null ? request.endDate() : quarter.getEndDate();
            if (start != null && end != null && start.isAfter(end)) {
                throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "시작일이 종료일보다 늦을 수 없습니다.");
            }
            if (request.startDate() != null) quarter.setStartDate(request.startDate());
            if (request.endDate() != null) quarter.setEndDate(request.endDate());
        }
        LocalDate businessResponseDueDate = request.businessResponseDueDate() != null
                ? request.businessResponseDueDate()
                : request.submissionDeadline();
        if (businessResponseDueDate != null) {
            quarter.setSubmissionDeadline(businessResponseDueDate);
        }
        quarterSettingRepository.save(quarter);
        return toResponse(quarter, countTargetPatents(quarter));
    }

    @Transactional
    public List<QuarterSettingResponse> updateReviewSchedule(int year, int mailLeadMonths, LocalDate businessResponseDueDate) {
        try {
            systemSettingsService.updateMailLeadMonths(mailLeadMonths);
        } catch (IllegalArgumentException exception) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, exception.getMessage());
        }
        List<QuarterSettingEntity> quarters = quarterSettingRepository.findByYearOrderByQuarterNumber(year);
        if (businessResponseDueDate != null) {
            quarters.forEach(quarter -> quarter.setSubmissionDeadline(businessResponseDueDate));
            quarterSettingRepository.saveAll(quarters);
        }
        return getQuarterSettings(year);
    }

    @Transactional(readOnly = true)
    public QuarterSettingResponse getActiveQuarter() {
        return quarterSettingRepository.findAll().stream()
                .filter(QuarterSettingEntity::isActivated)
                .findFirst()
                .map(q -> toResponse(q, countTargetPatents(q)))
                .orElse(null);
    }

    @Transactional
    public QuarterActivateResponse activateQuarter(String quarterKey) {
        QuarterSettingEntity quarter = findQuarter(quarterKey);
        if (quarter.getStartDate() == null || quarter.getEndDate() == null) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "분기 시작일과 종료일을 먼저 설정하세요.");
        }
        if (quarter.isActivated()) {
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS, "이미 활성화된 분기입니다.");
        }

        List<String> reviewStarted = patentReviewService.createQuarterReviewTargets(
                quarterKey, quarter.getStartDate(), quarter.getEndDate());

        quarter.setActivated(true);
        quarter.setActivatedAt(OffsetDateTime.now(KST));
        quarterSettingRepository.save(quarter);

        return new QuarterActivateResponse(quarterKey, reviewStarted.size(), 0,
                reviewStarted, List.of());
    }

    private QuarterSettingEntity findQuarter(String quarterKey) {
        return quarterSettingRepository.findById(quarterKey)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "분기 설정을 찾을 수 없습니다: " + quarterKey));
    }

    private int countTargetPatents(QuarterSettingEntity quarter) {
        if (quarter.getStartDate() == null || quarter.getEndDate() == null) return 0;
        return (int) patentReviewService.getAllPatents().stream()
                .filter(p -> p.feeDueDate() != null)
                .filter(p -> !p.feeDueDate().isBefore(quarter.getStartDate())
                        && !p.feeDueDate().isAfter(quarter.getEndDate()))
                .count();
    }

    @Transactional
    public QuarterSettingResponse endQuarter(String quarterKey) {
        QuarterSettingEntity quarter = findQuarter(quarterKey);
        if (!quarter.isActivated()) {
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS, "활성화된 분기만 종료할 수 있습니다.");
        }
        if (quarter.isEnded()) {
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS, "이미 종료된 분기입니다.");
        }
        quarter.setEnded(true);
        quarter.setEndedAt(OffsetDateTime.now(KST));
        quarterSettingRepository.save(quarter);
        return toResponse(quarter, countTargetPatents(quarter));
    }

    private QuarterSettingResponse toResponse(QuarterSettingEntity q, int targetCount) {
        int mailLeadMonths = systemSettingsService.getMailLeadMonths();
        return new QuarterSettingResponse(
                q.getQuarterKey(),
                q.getYear(),
                q.getQuarterNumber(),
                q.getYear() + "-Q" + q.getQuarterNumber(),
                q.getStartDate(),
                q.getEndDate(),
                q.isActivated(),
                q.getActivatedAt(),
                q.isEnded(),
                q.getEndedAt(),
                targetCount,
                q.getSubmissionDeadline(),
                q.getSubmissionDeadline(),
                mailLeadMonths,
                q.getStartDate() == null ? null : q.getStartDate().minusMonths(mailLeadMonths));
    }

    private void seedDefaultQuartersIfNeeded() {
        int year = LocalDate.now(KST).getYear();
        if (!quarterSettingRepository.findByYearOrderByQuarterNumber(year).isEmpty()) return;

        quarterSettingRepository.saveAll(List.of(
                new QuarterSettingEntity(year + "-Q1", year, 1,
                        LocalDate.of(year, 1, 1), LocalDate.of(year, 3, 31)),
                new QuarterSettingEntity(year + "-Q2", year, 2,
                        LocalDate.of(year, 4, 1), LocalDate.of(year, 6, 30)),
                new QuarterSettingEntity(year + "-Q3", year, 3,
                        LocalDate.of(year, 7, 1), LocalDate.of(year, 9, 30)),
                new QuarterSettingEntity(year + "-Q4", year, 4,
                        LocalDate.of(year, 10, 1), LocalDate.of(year, 12, 31))));
    }
}
