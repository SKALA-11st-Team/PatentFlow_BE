package com.syuuk.patentflow.settings.controller;

import com.syuuk.patentflow.common.dto.ClassificationRequest;
import com.syuuk.patentflow.common.dto.ClassificationResponse;
import com.syuuk.patentflow.common.dto.CountryExtensionRequest;
import com.syuuk.patentflow.common.dto.CountryExtensionResponse;
import com.syuuk.patentflow.common.dto.MailLeadMonthsResponse;
import com.syuuk.patentflow.common.dto.ResponseDeadlineResponse;
import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.settings.dto.QuarterActivateResponse;
import com.syuuk.patentflow.settings.dto.QuarterSettingRequest;
import com.syuuk.patentflow.settings.dto.QuarterSettingResponse;
import com.syuuk.patentflow.settings.dto.ReviewPeriodTemplateRequest;
import com.syuuk.patentflow.settings.dto.ReviewPeriodTemplateResponse;
import com.syuuk.patentflow.settings.dto.ValuationCriteriaRequest;
import com.syuuk.patentflow.settings.dto.ValuationCriteriaResponse;
import com.syuuk.patentflow.settings.dto.ValuationCriteriaVersionResponse;
import com.syuuk.patentflow.settings.service.SettingsService;
import com.syuuk.patentflow.settings.service.ValuationCriteriaService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final SystemSettingsService systemSettingsService;
    private final ValuationCriteriaService valuationCriteriaService;

    public SettingsController(
            SettingsService settingsService,
            SystemSettingsService systemSettingsService,
            ValuationCriteriaService valuationCriteriaService
    ) {
        this.settingsService = settingsService;
        this.systemSettingsService = systemSettingsService;
        this.valuationCriteriaService = valuationCriteriaService;
    }

    // ── AI 가치평가 기준 (UI-008) ─────────────────────────────
    // 축 가중치/등급 컷오프/유지 임계/subscore 배점을 버전 관리하며,
    // 변경은 이후 생성되는 AI 레포트부터 적용된다(레포트별 appliedCriteria 스냅샷으로 추적).

    @GetMapping("/valuation-criteria")
    public ApiResponse<ValuationCriteriaResponse> getValuationCriteria() {
        return ApiResponse.ok(valuationCriteriaService.getCurrent());
    }

    @PutMapping("/valuation-criteria")
    public ApiResponse<ValuationCriteriaResponse> updateValuationCriteria(
            @Valid @RequestBody ValuationCriteriaRequest request,
            org.springframework.security.core.Authentication authentication) {
        return ApiResponse.ok(valuationCriteriaService.update(request, currentActor(authentication)));
    }

    @GetMapping("/valuation-criteria/history")
    public ApiResponse<List<ValuationCriteriaVersionResponse>> getValuationCriteriaHistory() {
        return ApiResponse.ok(valuationCriteriaService.history());
    }

    // ── 분기 템플릿 ──────────────────────────────────────────
    // 연도 무관 분기 경계(월/일)를 관리자가 조회·수정하는 엔드포인트.
    // 여기서 변경하면 이후 활성화되는 모든 연도의 분기 날짜에 반영된다.

    @GetMapping("/review-periods")
    public ApiResponse<List<ReviewPeriodTemplateResponse>> getPeriodTemplates() {
        return ApiResponse.ok(settingsService.getPeriodTemplates());
    }

    @PutMapping("/review-periods/{periodNumber}")
    public ApiResponse<ReviewPeriodTemplateResponse> updatePeriodTemplate(
            @PathVariable int periodNumber,
            @Valid @RequestBody ReviewPeriodTemplateRequest request) {
        return ApiResponse.ok(settingsService.updatePeriodTemplate(periodNumber, request));
    }

    // ── 메일 발송 기준 개월 ────────────────────────────────────
    // 분기 시작 N개월 전에 스케줄러가 자동 활성화(메일 발송)를 트리거하는 기준.
    // 기존에는 다른 설정과 묶여 변경할 수 없었으므로 독립 엔드포인트로 분리했다.

    @GetMapping("/mail-lead-months")
    public ApiResponse<MailLeadMonthsResponse> getMailLeadMonths() {
        return ApiResponse.ok(new MailLeadMonthsResponse(systemSettingsService.getMailLeadMonths()));
    }

    @PatchMapping("/mail-lead-months")
    public ApiResponse<MailLeadMonthsResponse> updateMailLeadMonths(@Valid @RequestBody MailLeadMonthsResponse request) {
        int updated = systemSettingsService.updateMailLeadMonths(request.mailLeadMonths());
        return ApiResponse.ok(new MailLeadMonthsResponse(updated));
    }

    // ── 회신 기한 ─────────────────────────────────────────────
    // 분기 활성화 시 submissionDeadline = 활성화일(검토 시작일) + N개월 + M일.
    // 기본값 1개월 0일, 관리자가 개월+일 단위로 세밀하게 설정 가능.

    @GetMapping("/response-deadline")
    public ApiResponse<ResponseDeadlineResponse> getResponseDeadline() {
        return ApiResponse.ok(new ResponseDeadlineResponse(
                systemSettingsService.getResponseDeadlineMonths(),
                systemSettingsService.getResponseDeadlineDays()));
    }

    @PatchMapping("/response-deadline")
    public ApiResponse<ResponseDeadlineResponse> updateResponseDeadline(
            @Valid @RequestBody ResponseDeadlineResponse request) {
        systemSettingsService.updateResponseDeadline(request.months(), request.days());
        return ApiResponse.ok(new ResponseDeadlineResponse(
                systemSettingsService.getResponseDeadlineMonths(),
                systemSettingsService.getResponseDeadlineDays()));
    }

    // ── 분기 조회 / 활성화 ────────────────────────────────────

    @GetMapping("/review-quarters")
    public ApiResponse<List<QuarterSettingResponse>> getQuarterSettings(
            @RequestParam(defaultValue = "0") int year) {
        int resolvedYear = year > 0 ? year : LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        return ApiResponse.ok(settingsService.getQuarterSettings(resolvedYear));
    }

    @GetMapping("/review-quarters/active")
    public ApiResponse<QuarterSettingResponse> getActiveQuarter() {
        return ApiResponse.ok(settingsService.getActiveQuarter());
    }

    @PutMapping("/review-quarters/{quarterKey}")
    public ApiResponse<QuarterSettingResponse> updateQuarterSetting(
            @PathVariable String quarterKey,
            @Valid @RequestBody QuarterSettingRequest request) {
        return ApiResponse.ok(settingsService.updateQuarterSetting(quarterKey, request));
    }

    @PostMapping("/review-quarters/{quarterKey}/activate")
    public ApiResponse<QuarterActivateResponse> activateQuarter(@PathVariable String quarterKey) {
        return ApiResponse.ok(settingsService.activateQuarter(quarterKey));
    }

    // ── 국가별 연장 기간 ──────────────────────────────────────

    @GetMapping("/country-extensions")
    public ApiResponse<List<CountryExtensionResponse>> getCountryExtensions() {
        return ApiResponse.ok(systemSettingsService.getCountryExtensions());
    }

    @PutMapping("/country-extensions/{country}")
    public ApiResponse<CountryExtensionResponse> updateCountryExtension(
            @PathVariable String country,
            @Valid @RequestBody CountryExtensionRequest request) {
        return ApiResponse.ok(systemSettingsService.updateCountryExtension(country, request));
    }

    // ── 분류 ─────────────────────────────────────────────────

    @GetMapping("/classifications")
    public ApiResponse<List<ClassificationResponse>> getClassifications() {
        return ApiResponse.ok(systemSettingsService.getClassifications());
    }

    @PostMapping("/classifications/{type}")
    public ApiResponse<ClassificationResponse> addClassification(
            @PathVariable String type,
            @Valid @RequestBody ClassificationRequest request) {
        return ApiResponse.ok(systemSettingsService.addClassification(type, request.value()));
    }

    @PutMapping("/classifications/{type}/{value}")
    public ApiResponse<ClassificationResponse> renameClassification(
            @PathVariable String type,
            @PathVariable String value,
            @Valid @RequestBody ClassificationRequest request) {
        return ApiResponse.ok(systemSettingsService.renameClassification(type, value, request.value()));
    }

    @DeleteMapping("/classifications/{type}/{value}")
    public ApiResponse<ClassificationResponse> deleteClassification(
            @PathVariable String type,
            @PathVariable String value) {
        return ApiResponse.ok(systemSettingsService.deleteClassification(type, value));
    }

    // 인증 주체 표시명 추출(PatentController.currentActor와 동일 규칙).
    private String currentActor(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "관리자";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.syuuk.patentflow.auth.dto.UserPrincipalResponse userPrincipal) {
            if (userPrincipal.username() != null && !userPrincipal.username().isBlank()) {
                return userPrincipal.username().trim();
            }
            if (userPrincipal.email() != null && !userPrincipal.email().isBlank()) {
                return userPrincipal.email().trim();
            }
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? "관리자" : name.trim();
    }
}
