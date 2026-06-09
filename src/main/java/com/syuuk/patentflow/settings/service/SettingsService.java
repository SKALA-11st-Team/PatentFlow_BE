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
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int[][] FIXED_QUARTER_BOUNDARIES = {
            {1, 1, 3, 31},
            {4, 1, 6, 30},
            {7, 1, 9, 30},
            {10, 1, 12, 31}
    };

    private final QuarterSettingRepository quarterSettingRepository;
    private final ReviewPeriodTemplateRepository periodTemplateRepository;
    private final PatentReviewService patentReviewService;
    private final AiReportBatchService aiReportBatchService;
    private final SystemSettingsService systemSettingsService;
    private final Object quarterActivationMonitor = new Object();

    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public SettingsService(
            QuarterSettingRepository quarterSettingRepository,
            ReviewPeriodTemplateRepository periodTemplateRepository,
            PatentReviewService patentReviewService,
            AiReportBatchService aiReportBatchService,
            SystemSettingsService systemSettingsService,
            org.springframework.context.ApplicationEventPublisher eventPublisher
    ) {
        this.quarterSettingRepository = quarterSettingRepository;
        this.periodTemplateRepository = periodTemplateRepository;
        this.patentReviewService = patentReviewService;
        this.aiReportBatchService = aiReportBatchService;
        this.systemSettingsService = systemSettingsService;
        this.eventPublisher = eventPublisher;
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
        validatePeriodNumber(periodNumber);
        ReviewPeriodTemplateEntity template = periodTemplateRepository.findById(periodNumber)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "분기 템플릿을 찾을 수 없습니다: Q" + periodNumber));
        validateDateRange(periodNumber, request.startMonth(), request.startDay(), request.endMonth(), request.endDay());
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
        return quarterSettingRepository.findByActivatedTrueAndEndedFalseOrderByActivatedAtDesc().stream()
                .findFirst()
                .map(q -> toResponse(q, q.getQuarterKey(), q.getYear(), q.getQuarterNumber(),
                        q.getStartDate(), q.getEndDate()))
                .orElse(null);
    }

    @Transactional
    public QuarterSettingResponse updateQuarterSetting(String quarterKey, QuarterSettingRequest request) {
        QuarterSettingEntity quarter = findOrCreateQuarter(quarterKey);
        validateQuarterDates(quarter.getYear(), quarter.getStartDate(), quarter.getEndDate());
        if (request.startDate() != null || request.endDate() != null) {
            if (quarter.isActivated()) {
                throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS, "이미 활성화된 분기는 납부 기간을 수정할 수 없습니다.");
            }
            LocalDate start = request.startDate() != null ? request.startDate() : quarter.getStartDate();
            LocalDate end = request.endDate() != null ? request.endDate() : quarter.getEndDate();
            validateQuarterDates(quarter.getYear(), start, end);
            if (request.startDate() != null) quarter.setStartDate(request.startDate());
            if (request.endDate() != null) quarter.setEndDate(request.endDate());
        }
        LocalDate deadline = request.businessResponseDueDate() != null
                ? request.businessResponseDueDate()
                : request.submissionDeadline();
        if (deadline != null) {
            validateBusinessResponseDueDate(quarter, deadline);
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
            quarters.forEach(quarter -> {
                validateBusinessResponseDueDate(quarter, businessResponseDueDate);
                quarter.setSubmissionDeadline(businessResponseDueDate);
            });
            quarterSettingRepository.saveAll(quarters);
        }
        return getQuarterSettings(year);
    }

    @Transactional
    public QuarterActivateResponse activateQuarter(String quarterKey) {
        synchronized (quarterActivationMonitor) {
            QuarterSettingEntity quarter = findOrCreateQuarterForUpdate(quarterKey);
            if (quarter.isActivated() && !quarter.isEnded()) {
                return buildActivateResponse(quarter);
            }
            validateActivatable(quarter);
            ensureNoOtherActiveQuarter(quarter);
            doActivate(quarter);
            return buildActivateResponse(quarter);
        }
    }

    @Transactional
    public boolean activateQuarterIfNeeded(String quarterKey) {
        synchronized (quarterActivationMonitor) {
            QuarterSettingEntity quarter = findOrCreateQuarterForUpdate(quarterKey);
            if (quarter.isActivated() && !quarter.isEnded()) {
                return false;
            }
            validateActivatable(quarter);
            ensureNoOtherActiveQuarter(quarter);
            doActivate(quarter);
            return true;
        }
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

        LocalDate businessResponseDueDate = activationDate.plusMonths(responseDeadlineMonths).plusDays(responseDeadlineDays);
        validateBusinessResponseDueDate(quarter, businessResponseDueDate);
        quarter.setSubmissionDeadline(businessResponseDueDate);
        quarter.setMailLeadMonthsSnapshot(mailLeadMonths);
        quarter.setActivated(true);
        quarter.setActivatedAt(OffsetDateTime.now(KST));
        quarterSettingRepository.save(quarter);

        List<String> reviewStartedIds = patentReviewService.createQuarterReviewTargets(
                quarter.getQuarterKey(), quarter.getStartDate(), quarter.getEndDate());
        if (!reviewStartedIds.isEmpty()) {
            aiReportBatchService.generateReportsForQuarter(reviewStartedIds, quarter.getQuarterKey());
        }
        // NOTI-04: 분기 검토 시작을 사업부에 알림(커밋 후 발행).
        eventPublisher.publishEvent(new com.syuuk.patentflow.notification.event.WorkflowNotificationEvent(
                "검토 분기 시작",
                "%s 연차료 검토가 시작되었습니다. 배정 특허 %d건을 확인해주세요."
                        .formatted(quarter.getQuarterKey(), reviewStartedIds.size()),
                "BUSINESS",
                "/business/review-requests"));
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
        return quarterSettingRepository.findById(quarterKey)
                .orElseGet(() -> quarterSettingRepository.save(createQuarter(quarterKey)));
    }

    private QuarterSettingEntity findOrCreateQuarterForUpdate(String quarterKey) {
        return quarterSettingRepository.findByIdForUpdate(quarterKey).orElseGet(() -> {
            QuarterSettingEntity created = createQuarter(quarterKey);
            quarterSettingRepository.saveAndFlush(created);
            return quarterSettingRepository.findByIdForUpdate(quarterKey).orElse(created);
        });
    }

    private QuarterSettingEntity createQuarter(String quarterKey) {
        QuarterParts parts = parseQuarterKey(quarterKey);
        ReviewPeriodTemplateEntity template = periodTemplateRepository.findById(parts.quarterNumber())
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "분기 템플릿을 찾을 수 없습니다: Q" + parts.quarterNumber()));
        LocalDate startDate = LocalDate.of(parts.year(), template.getStartMonth(), template.getStartDay());
        LocalDate endDate = LocalDate.of(parts.year(), template.getEndMonth(), template.getEndDay());
        validateQuarterDates(parts.year(), startDate, endDate);
        return new QuarterSettingEntity(quarterKey, parts.year(), parts.quarterNumber(), startDate, endDate);
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

    private void validateDateRange(int periodNumber, int startMonth, int startDay, int endMonth, int endDay) {
        validatePeriodNumber(periodNumber);
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.of(2000, startMonth, startDay);
            end = LocalDate.of(2000, endMonth, endDay);
        } catch (DateTimeException e) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "유효하지 않은 분기 경계 날짜입니다.");
        }
        if (!start.isBefore(end)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "시작일은 종료일보다 빨라야 합니다.");
        }
        int[] boundary = FIXED_QUARTER_BOUNDARIES[periodNumber - 1];
        LocalDate fixedStart = LocalDate.of(2000, boundary[0], boundary[1]);
        LocalDate fixedEnd = LocalDate.of(2000, boundary[2], boundary[3]);
        if (start.isBefore(fixedStart) || end.isAfter(fixedEnd)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "분기 경계는 고정된 달력 분기 범위 안에서만 설정할 수 있습니다.");
        }
    }

    private void validatePeriodNumber(int periodNumber) {
        if (periodNumber < 1 || periodNumber > 4) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "분기는 Q1부터 Q4까지만 사용할 수 있습니다.");
        }
    }

    // 회귀: 분기 번호는 1~4로 clamp되지만 연도는 무경계(Integer.parseInt)라 99999/0/음수 같은 비정상 연도가
    // 분기 엔티티로 생성될 수 있었다. 합리적 달력 범위로 clamp한다(시드/스케줄러와 충돌하지 않도록 넉넉히).
    private void validateQuarterYear(int year) {
        if (year < 2000 || year > 2100) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "유효하지 않은 분기 연도입니다: " + year);
        }
    }

    private QuarterParts parseQuarterKey(String quarterKey) {
        String[] parts = quarterKey == null ? new String[0] : quarterKey.split("-Q");
        if (parts.length != 2) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "잘못된 분기 키 형식입니다: " + quarterKey);
        }
        try {
            int year = Integer.parseInt(parts[0]);
            int quarterNumber = Integer.parseInt(parts[1]);
            validateQuarterYear(year);
            validatePeriodNumber(quarterNumber);
            return new QuarterParts(year, quarterNumber);
        } catch (NumberFormatException e) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "잘못된 분기 키 형식입니다: " + quarterKey);
        }
    }

    private void validateQuarterDates(int year, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "분기 시작일과 종료일은 모두 필요합니다.");
        }
        if (startDate.getYear() != year || endDate.getYear() != year) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "분기 시작일과 종료일은 분기 키의 연도와 일치해야 합니다.");
        }
        if (startDate.isAfter(endDate)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "시작일이 종료일보다 늦을 수 없습니다.");
        }
    }

    private void validateBusinessResponseDueDate(QuarterSettingEntity quarter, LocalDate dueDate) {
        validateQuarterDates(quarter.getYear(), quarter.getStartDate(), quarter.getEndDate());
        LocalDate earliest = quarter.isActivated() && quarter.getActivatedAt() != null
                ? quarter.getActivatedAt().toLocalDate()
                : quarter.getStartDate().minusMonths(systemSettingsService.getMailLeadMonths());
        if (dueDate.isBefore(earliest)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "회신 기한은 검토 요청 가능일보다 빠를 수 없습니다.");
        }
        if (dueDate.isAfter(quarter.getEndDate())) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "회신 기한은 분기 종료일보다 늦을 수 없습니다.");
        }
    }

    private void validateActivatable(QuarterSettingEntity quarter) {
        if (quarter.isEnded()) {
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS, "이미 종료된 분기는 다시 활성화할 수 없습니다.");
        }
        validateQuarterDates(quarter.getYear(), quarter.getStartDate(), quarter.getEndDate());
    }

    private void ensureNoOtherActiveQuarter(QuarterSettingEntity quarter) {
        quarterSettingRepository.findActiveForUpdate().stream()
                .filter(active -> !active.getQuarterKey().equals(quarter.getQuarterKey()))
                .findFirst()
                .ifPresent(active -> {
                    throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS,
                            "이미 활성화된 분기가 있습니다: " + active.getQuarterKey());
                });
    }

    private record QuarterParts(int year, int quarterNumber) {}

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
