package com.syuuk.patentflow.settings.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.patent.dto.PatentLifecycleStatus;
import com.syuuk.patentflow.patent.service.AiReportBatchService;
import com.syuuk.patentflow.patent.service.PatentReviewService;
import com.syuuk.patentflow.settings.domain.QuarterSettingEntity;
import com.syuuk.patentflow.settings.domain.ReviewPeriodTemplateEntity;
import com.syuuk.patentflow.settings.dto.QuarterActivateResponse;
import com.syuuk.patentflow.settings.dto.QuarterSettingRequest;
import com.syuuk.patentflow.settings.dto.QuarterSettingResponse;
import com.syuuk.patentflow.settings.dto.ReviewPeriodTemplateRequest;
import com.syuuk.patentflow.settings.dto.ReviewPeriodTemplateResponse;
import com.syuuk.patentflow.settings.repository.QuarterSettingRepository;
import com.syuuk.patentflow.settings.repository.ReviewPeriodTemplateRepository;
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
    private final ReviewPeriodTemplateRepository periodTemplateRepository;
    private final PatentReviewService patentReviewService;
    private final AiReportBatchService aiReportBatchService;
    private final SystemSettingsService systemSettingsService;

    public SettingsService(
            QuarterSettingRepository quarterSettingRepository,
            ReviewPeriodTemplateRepository periodTemplateRepository,
            PatentReviewService patentReviewService,
            AiReportBatchService aiReportBatchService,
            SystemSettingsService systemSettingsService
    ) {
        this.quarterSettingRepository = quarterSettingRepository;
        this.periodTemplateRepository = periodTemplateRepository;
        this.patentReviewService = patentReviewService;
        this.aiReportBatchService = aiReportBatchService;
        this.systemSettingsService = systemSettingsService;
        seedDefaultTemplatesIfNeeded();
    }

    @Transactional(readOnly = true)
    public List<ReviewPeriodTemplateResponse> getPeriodTemplates() {
        return periodTemplateRepository.findAllByOrderByPeriodNumber().stream()
                .map(this::toTemplateResponse)
                .toList();
    }

    @Transactional
    public ReviewPeriodTemplateResponse updatePeriodTemplate(int periodNumber, ReviewPeriodTemplateRequest request) {
        ReviewPeriodTemplateEntity template = periodTemplateRepository.findById(periodNumber)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "분기 템플릿을 찾을 수 없습니다: Q" + periodNumber));
        validateDateRange(request.startMonth(), request.startDay(), request.endMonth(), request.endDay());
        template.update(request.startMonth(), request.startDay(), request.endMonth(), request.endDay());
        periodTemplateRepository.save(template);
        return toTemplateResponse(template);
    }

    @Transactional(readOnly = true)
    public List<QuarterSettingResponse> getQuarterSettings(int year) {
        return periodTemplateRepository.findAllByOrderByPeriodNumber().stream()
                .map(template -> {
                    String quarterKey = year + "-Q" + template.getPeriodNumber();
                    QuarterSettingEntity entity = quarterSettingRepository.findById(quarterKey).orElse(null);
                    LocalDate startDate = entity != null && entity.getStartDate() != null
                            ? entity.getStartDate()
                            : LocalDate.of(year, template.getStartMonth(), template.getStartDay());
                    LocalDate endDate = entity != null && entity.getEndDate() != null
                            ? entity.getEndDate()
                            : LocalDate.of(year, template.getEndMonth(), template.getEndDay());
                    return toResponse(entity, quarterKey, year, template.getPeriodNumber(), startDate, endDate);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public QuarterSettingResponse getActiveQuarter() {
        return quarterSettingRepository.findAll().stream()
                .filter(QuarterSettingEntity::isActivated)
                .findFirst()
                .map(q -> toResponse(q, q.getQuarterKey(), q.getYear(), q.getQuarterNumber(),
                        q.getStartDate(), q.getEndDate()))
                .orElse(null);
    }

    @Transactional
    public QuarterSettingResponse updateQuarterSetting(String quarterKey, QuarterSettingRequest request) {
        QuarterSettingEntity quarter = findOrCreateQuarter(quarterKey);
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
        LocalDate deadline = request.businessResponseDueDate() != null
                ? request.businessResponseDueDate()
                : request.submissionDeadline();
        if (deadline != null) {
            quarter.setSubmissionDeadline(deadline);
        }
        quarterSettingRepository.save(quarter);
        return toResponse(quarter, quarter.getQuarterKey(), quarter.getYear(),
                quarter.getQuarterNumber(), quarter.getStartDate(), quarter.getEndDate());
    }

    @Transactional
    public List<QuarterSettingResponse> updateReviewSchedule(int year, int mailLeadMonths, LocalDate businessResponseDueDate) {
        systemSettingsService.updateMailLeadMonths(mailLeadMonths);
        List<QuarterSettingEntity> quarters = quarterSettingRepository.findByYearOrderByQuarterNumber(year);
        if (businessResponseDueDate != null) {
            quarters.forEach(quarter -> quarter.setSubmissionDeadline(businessResponseDueDate));
            quarterSettingRepository.saveAll(quarters);
        }
        return getQuarterSettings(year);
    }

    @Transactional
    public QuarterActivateResponse activateQuarter(String quarterKey) {
        QuarterSettingEntity quarter = findOrCreateQuarter(quarterKey);
        if (quarter.isActivated()) {
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS, "이미 활성화된 분기입니다.");
        }
        doActivate(quarter);
        return buildActivateResponse(quarter);
    }

    @Transactional
    public boolean activateQuarterIfNeeded(String quarterKey) {
        QuarterSettingEntity quarter = findOrCreateQuarter(quarterKey);
        if (quarter.isActivated()) {
            return false;
        }
        doActivate(quarter);
        return true;
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
        return toResponse(quarter, quarter.getQuarterKey(), quarter.getYear(),
                quarter.getQuarterNumber(), quarter.getStartDate(), quarter.getEndDate());
    }

    private void doActivate(QuarterSettingEntity quarter) {
        int responseDeadlineMonths = systemSettingsService.getResponseDeadlineMonths();
        int responseDeadlineDays = systemSettingsService.getResponseDeadlineDays();
        int mailLeadMonths = systemSettingsService.getMailLeadMonths();
        LocalDate activationDate = LocalDate.now(KST);

        quarter.setSubmissionDeadline(activationDate.plusMonths(responseDeadlineMonths).plusDays(responseDeadlineDays));
        quarter.setMailLeadMonthsSnapshot(mailLeadMonths);
        quarter.setActivated(true);
        quarter.setActivatedAt(OffsetDateTime.now(KST));
        quarterSettingRepository.save(quarter);

        List<String> reviewStartedIds = patentReviewService.createQuarterReviewTargets(
                quarter.getQuarterKey(), quarter.getStartDate(), quarter.getEndDate());
        if (!reviewStartedIds.isEmpty()) {
            aiReportBatchService.generateReportsForQuarter(reviewStartedIds, quarter.getQuarterKey());
        }
    }

    private QuarterActivateResponse buildActivateResponse(QuarterSettingEntity quarter) {
        List<String> targets = patentReviewService.getAllPatents().stream()
                .filter(p -> p.feeDueDate() != null
                        && p.lifecycleStatus() == PatentLifecycleStatus.ACTIVE
                        && !p.feeDueDate().isBefore(quarter.getStartDate())
                        && !p.feeDueDate().isAfter(quarter.getEndDate()))
                .map(p -> p.patentId())
                .toList();
        return new QuarterActivateResponse(quarter.getQuarterKey(), targets.size(), 0, targets, List.of());
    }

    private QuarterSettingEntity findOrCreateQuarter(String quarterKey) {
        return quarterSettingRepository.findById(quarterKey).orElseGet(() -> {
            String[] parts = quarterKey.split("-Q");
            if (parts.length != 2) {
                throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "잘못된 분기 키 형식입니다: " + quarterKey);
            }
            int year = Integer.parseInt(parts[0]);
            int periodNumber = Integer.parseInt(parts[1]);
            ReviewPeriodTemplateEntity template = periodTemplateRepository.findById(periodNumber)
                    .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                            "분기 템플릿을 찾을 수 없습니다: Q" + periodNumber));
            LocalDate startDate = LocalDate.of(year, template.getStartMonth(), template.getStartDay());
            LocalDate endDate = LocalDate.of(year, template.getEndMonth(), template.getEndDay());
            return quarterSettingRepository.save(
                    new QuarterSettingEntity(quarterKey, year, periodNumber, startDate, endDate));
        });
    }

    private QuarterSettingEntity findQuarter(String quarterKey) {
        return quarterSettingRepository.findById(quarterKey)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "분기 설정을 찾을 수 없습니다: " + quarterKey));
    }

    private int countTargetPatents(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        return (int) patentReviewService.getAllPatents().stream()
                .filter(p -> p.feeDueDate() != null
                        && p.lifecycleStatus() == PatentLifecycleStatus.ACTIVE
                        && !p.feeDueDate().isBefore(startDate)
                        && !p.feeDueDate().isAfter(endDate))
                .count();
    }

    private QuarterSettingResponse toResponse(
            QuarterSettingEntity entity,
            String quarterKey,
            int year,
            int periodNumber,
            LocalDate startDate,
            LocalDate endDate
    ) {
        int currentMailLeadMonths = systemSettingsService.getMailLeadMonths();
        boolean activated = entity != null && entity.isActivated();
        OffsetDateTime activatedAt = entity != null ? entity.getActivatedAt() : null;
        boolean ended = entity != null && entity.isEnded();
        OffsetDateTime endedAt = entity != null ? entity.getEndedAt() : null;
        LocalDate submissionDeadline = entity != null ? entity.getSubmissionDeadline() : null;
        int targetCount = activated ? countTargetPatents(startDate, endDate) : 0;
        Integer snapshot = entity != null ? entity.getMailLeadMonthsSnapshot() : null;
        int mailLeadMonths = activated && snapshot != null ? snapshot : currentMailLeadMonths;
        LocalDate scheduledMailSendDate = startDate != null ? startDate.minusMonths(mailLeadMonths) : null;

        return new QuarterSettingResponse(
                quarterKey,
                year,
                periodNumber,
                year + "-Q" + periodNumber,
                startDate,
                endDate,
                activated,
                activatedAt,
                ended,
                endedAt,
                targetCount,
                submissionDeadline,
                submissionDeadline,
                mailLeadMonths,
                scheduledMailSendDate);
    }

    private ReviewPeriodTemplateResponse toTemplateResponse(ReviewPeriodTemplateEntity template) {
        String label = String.format("Q%d (%d/%d ~ %d/%d)",
                template.getPeriodNumber(),
                template.getStartMonth(),
                template.getStartDay(),
                template.getEndMonth(),
                template.getEndDay());
        return new ReviewPeriodTemplateResponse(
                template.getPeriodNumber(),
                template.getStartMonth(),
                template.getStartDay(),
                template.getEndMonth(),
                template.getEndDay(),
                label);
    }

    private void validateDateRange(int startMonth, int startDay, int endMonth, int endDay) {
        LocalDate start = LocalDate.of(2000, startMonth, startDay);
        LocalDate end = LocalDate.of(2000, endMonth, endDay);
        if (!start.isBefore(end)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "시작일이 종료일보다 늦을 수 없습니다.");
        }
    }

    private void seedDefaultTemplatesIfNeeded() {
        if (!periodTemplateRepository.findAll().isEmpty()) {
            return;
        }
        periodTemplateRepository.saveAll(List.of(
                new ReviewPeriodTemplateEntity(1, 1, 1, 3, 31),
                new ReviewPeriodTemplateEntity(2, 4, 1, 6, 30),
                new ReviewPeriodTemplateEntity(3, 7, 1, 9, 30),
                new ReviewPeriodTemplateEntity(4, 10, 1, 12, 31)));
    }
}
