package com.syuuk.patentflow.settings.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.settings.domain.QuarterSettingEntity;
import com.syuuk.patentflow.settings.domain.ReviewPeriodTemplateEntity;
import com.syuuk.patentflow.settings.dto.QuarterActivateResponse;
import com.syuuk.patentflow.settings.dto.QuarterSettingRequest;
import com.syuuk.patentflow.settings.dto.QuarterSettingResponse;
import com.syuuk.patentflow.settings.dto.ReviewPeriodTemplateRequest;
import com.syuuk.patentflow.settings.dto.ReviewPeriodTemplateResponse;
import com.syuuk.patentflow.settings.repository.QuarterSettingRepository;
import com.syuuk.patentflow.settings.repository.ReviewPeriodTemplateRepository;
import com.syuuk.patentflow.patent.service.AiReportBatchService;
import com.syuuk.patentflow.patent.service.PatentReviewService;
import com.syuuk.patentflow.patent.service.PatentWorkflowService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final QuarterSettingRepository quarterSettingRepository;
    private final ReviewPeriodTemplateRepository periodTemplateRepository;
    private final PatentReviewService patentReviewService;
    private final PatentWorkflowService patentWorkflowService;
    private final AiReportBatchService aiReportBatchService;
    private final SystemSettingsService systemSettingsService;

    public SettingsService(
            QuarterSettingRepository quarterSettingRepository,
            ReviewPeriodTemplateRepository periodTemplateRepository,
            PatentReviewService patentReviewService,
            PatentWorkflowService patentWorkflowService,
            AiReportBatchService aiReportBatchService,
            SystemSettingsService systemSettingsService) {
        this.quarterSettingRepository = quarterSettingRepository;
        this.periodTemplateRepository = periodTemplateRepository;
        this.patentReviewService = patentReviewService;
        this.patentWorkflowService = patentWorkflowService;
        this.aiReportBatchService = aiReportBatchService;
        this.systemSettingsService = systemSettingsService;
        seedDefaultTemplatesIfNeeded();
    }

    // ── 분기 템플릿 ──────────────────────────────────────────

    public List<ReviewPeriodTemplateResponse> getPeriodTemplates() {
        return periodTemplateRepository.findAllByOrderByPeriodNumber().stream()
                .map(this::toTemplateResponse)
                .toList();
    }

    public ReviewPeriodTemplateResponse updatePeriodTemplate(int periodNumber, ReviewPeriodTemplateRequest request) {
        ReviewPeriodTemplateEntity template = periodTemplateRepository.findById(periodNumber)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "분기 템플릿을 찾을 수 없습니다: Q" + periodNumber));
        validateDateRange(request.startMonth(), request.startDay(), request.endMonth(), request.endDay());
        template.update(request.startMonth(), request.startDay(), request.endMonth(), request.endDay());
        periodTemplateRepository.save(template);
        return toTemplateResponse(template);
    }

    // ── 분기 조회 ─────────────────────────────────────────────

    // DB에 레코드가 없는 미래 분기도 템플릿으로 계산해 표시하므로
    // 연도별 사전 시드 없이 getQuarterSettings(2027) 등이 항상 동작한다.
    public List<QuarterSettingResponse> getQuarterSettings(int year) {
        List<ReviewPeriodTemplateEntity> templates = periodTemplateRepository.findAllByOrderByPeriodNumber();
        return templates.stream()
                .map(template -> {
                    String quarterKey = year + "-Q" + template.getPeriodNumber();
                    QuarterSettingEntity entity = quarterSettingRepository.findById(quarterKey).orElse(null);
                    LocalDate startDate = (entity != null && entity.getStartDate() != null)
                            ? entity.getStartDate()
                            : LocalDate.of(year, template.getStartMonth(), template.getStartDay());
                    LocalDate endDate = (entity != null && entity.getEndDate() != null)
                            ? entity.getEndDate()
                            : LocalDate.of(year, template.getEndMonth(), template.getEndDay());
                    return toResponse(entity, quarterKey, year, template.getPeriodNumber(), startDate, endDate);
                })
                .toList();
    }

    public QuarterSettingResponse getActiveQuarter() {
        return quarterSettingRepository.findAll().stream()
                .filter(QuarterSettingEntity::isActivated)
                .findFirst()
                .map(q -> toResponse(q, q.getQuarterKey(), q.getYear(), q.getQuarterNumber(),
                        q.getStartDate(), q.getEndDate()))
                .orElse(null);
    }

    public QuarterSettingResponse updateQuarterSetting(String quarterKey, QuarterSettingRequest request) {
        QuarterSettingEntity quarter = findQuarter(quarterKey);
        if (quarter.isActivated()) {
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS, "이미 활성화된 분기는 납부 기간을 수정할 수 없습니다.");
        }
        if (request.startDate() != null) quarter.setStartDate(request.startDate());
        if (request.endDate() != null) quarter.setEndDate(request.endDate());
        LocalDate deadline = request.businessResponseDueDate() != null
                ? request.businessResponseDueDate() : request.submissionDeadline();
        if (deadline != null) quarter.setSubmissionDeadline(deadline);
        quarterSettingRepository.save(quarter);
        return toResponse(quarter, quarter.getQuarterKey(), quarter.getYear(),
                quarter.getQuarterNumber(), quarter.getStartDate(), quarter.getEndDate());
    }

    // ── 분기 활성화 ───────────────────────────────────────────

    public QuarterActivateResponse activateQuarter(String quarterKey) {
        QuarterSettingEntity quarter = findOrCreateQuarter(quarterKey);
        if (quarter.isActivated()) {
            throw new PatentFlowException(ErrorCode.INVALID_WORKFLOW_STATUS, "이미 활성화된 분기입니다.");
        }
        doActivate(quarter);
        return buildActivateResponse(quarter);
    }

    // 스케줄러 전용 — 이미 활성화된 경우 조용히 스킵하고 false 반환
    public boolean activateQuarterIfNeeded(String quarterKey) {
        QuarterSettingEntity quarter = findOrCreateQuarter(quarterKey);
        if (!quarter.isActivated()) {
            doActivate(quarter);
            return true;
        }
        return false;
    }

    private void doActivate(QuarterSettingEntity quarter) {
        int responseDeadlineMonths = systemSettingsService.getResponseDeadlineMonths();
        int responseDeadlineDays = systemSettingsService.getResponseDeadlineDays();
        int mailLeadMonths = systemSettingsService.getMailLeadMonths();
        // submissionDeadline = 검토 시작일(오늘) + N개월 + M일
        // 사업부 입장에서 "검토 요청을 받은 날부터 N개월 안에 회신"이 자연스럽고,
        // 분기 시작일 기준 역산보다 실제 업무 흐름과 일치한다.
        LocalDate activationDate = LocalDate.now(KST);
        quarter.setSubmissionDeadline(
                activationDate.plusMonths(responseDeadlineMonths).plusDays(responseDeadlineDays));
        // 활성화 시점의 mailLeadMonths를 스냅샷으로 저장 — 이후 설정 변경 시에도 이력은 고정
        quarter.setMailLeadMonthsSnapshot(mailLeadMonths);
        quarter.setActivated(true);
        quarter.setActivatedAt(OffsetDateTime.now(KST));
        quarterSettingRepository.save(quarter);
        // 검토 시작일에 도달한 분기에 대해, 해당 납부 기간에 속한 ACTIVE 특허만 검토 대상으로 만든다.
        List<String> reviewStartedIds = patentWorkflowService.createQuarterReviewTargets(
                quarter.getQuarterKey(), quarter.getStartDate(), quarter.getEndDate());
        // 대상 특허가 있으면 백그라운드에서 AI 레포트 배치 생성을 시작한다.
        // @Async이므로 이 메서드는 즉시 반환되고, 실제 생성은 별도 스레드에서 순차 진행된다.
        if (!reviewStartedIds.isEmpty()) {
            aiReportBatchService.generateReportsForQuarter(reviewStartedIds, quarter.getQuarterKey());
        }
    }

    private QuarterActivateResponse buildActivateResponse(QuarterSettingEntity quarter) {
        List<String> targets = patentReviewService.getAllPatents().stream()
                .filter(p -> p.feeDueDate() != null
                        && p.lifecycleStatus() == com.syuuk.patentflow.patent.dto.PatentLifecycleStatus.ACTIVE
                        && !p.feeDueDate().isBefore(quarter.getStartDate())
                        && !p.feeDueDate().isAfter(quarter.getEndDate()))
                .map(p -> p.patentId())
                .toList();
        return new QuarterActivateResponse(quarter.getQuarterKey(), targets.size(), 0, targets, List.of());
    }

    // ── 내부 유틸 ─────────────────────────────────────────────

    // 분기 레코드를 미리 시드하지 않고, 활성화 요청 시점에 템플릿으로 생성한다.
    // 이렇게 해야 연도별 사전 데이터 없이 임의 연도 분기를 활성화할 수 있다.
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
        if (startDate == null || endDate == null) return 0;
        return (int) patentReviewService.getAllPatents().stream()
                .filter(p -> p.feeDueDate() != null
                        && p.lifecycleStatus() == com.syuuk.patentflow.patent.dto.PatentLifecycleStatus.ACTIVE
                        && !p.feeDueDate().isBefore(startDate)
                        && !p.feeDueDate().isAfter(endDate))
                .count();
    }

    private QuarterSettingResponse toResponse(
            QuarterSettingEntity entity, String quarterKey, int year, int periodNumber,
            LocalDate startDate, LocalDate endDate) {
        int currentMailLeadMonths = systemSettingsService.getMailLeadMonths();
        boolean activated = entity != null && entity.isActivated();
        OffsetDateTime activatedAt = entity != null ? entity.getActivatedAt() : null;
        boolean ended = entity != null && entity.isEnded();
        OffsetDateTime endedAt = entity != null ? entity.getEndedAt() : null;
        LocalDate submissionDeadline = entity != null ? entity.getSubmissionDeadline() : null;
        int targetCount = activated ? countTargetPatents(startDate, endDate) : 0;
        // 활성화된 분기는 활성화 시점에 저장한 스냅샷 사용 → 이후 설정 변경에도 이력 고정
        // 미활성 예정 분기는 현재 설정값으로 예상 발송일 계산
        Integer snapshot = (entity != null) ? entity.getMailLeadMonthsSnapshot() : null;
        int mailLeadMonths = (activated && snapshot != null) ? snapshot : currentMailLeadMonths;
        LocalDate scheduledMailSendDate = startDate != null ? startDate.minusMonths(mailLeadMonths) : null;

        return new QuarterSettingResponse(
                quarterKey, year, periodNumber,
                year + "-Q" + periodNumber,
                startDate, endDate,
                activated, activatedAt,
                ended, endedAt,
                targetCount,
                submissionDeadline,
                submissionDeadline,
                mailLeadMonths,
                scheduledMailSendDate);
    }

    private ReviewPeriodTemplateResponse toTemplateResponse(ReviewPeriodTemplateEntity t) {
        String label = String.format("Q%d (%d/%d ~ %d/%d)",
                t.getPeriodNumber(), t.getStartMonth(), t.getStartDay(),
                t.getEndMonth(), t.getEndDay());
        return new ReviewPeriodTemplateResponse(
                t.getPeriodNumber(), t.getStartMonth(), t.getStartDay(),
                t.getEndMonth(), t.getEndDay(), label);
    }

    private void validateDateRange(int startMonth, int startDay, int endMonth, int endDay) {
        LocalDate start = LocalDate.of(2000, startMonth, startDay);
        LocalDate end = LocalDate.of(2000, endMonth, endDay);
        if (!start.isBefore(end)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "시작일이 종료일보다 늦을 수 없습니다.");
        }
    }

    // 앱 최초 기동 시 review_period_templates 테이블이 비어 있으면 Q1~Q4 기본 경계를 한 번만 삽입한다.
    // SQL 시드 파일 없이 코드로 관리하여 DB 초기화 순서에 무관하게 동작한다.
    private void seedDefaultTemplatesIfNeeded() {
        if (!periodTemplateRepository.findAll().isEmpty()) return;
        periodTemplateRepository.saveAll(List.of(
                new ReviewPeriodTemplateEntity(1, 1, 1, 3, 31),
                new ReviewPeriodTemplateEntity(2, 4, 1, 6, 30),
                new ReviewPeriodTemplateEntity(3, 7, 1, 9, 30),
                new ReviewPeriodTemplateEntity(4, 10, 1, 12, 31)));
    }
}
