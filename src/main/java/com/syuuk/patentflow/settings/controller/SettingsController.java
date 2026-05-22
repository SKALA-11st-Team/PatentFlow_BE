package com.syuuk.patentflow.settings.controller;

import com.syuuk.patentflow.common.dto.ClassificationRequest;
import com.syuuk.patentflow.common.dto.ClassificationResponse;
import com.syuuk.patentflow.common.dto.CountryExtensionRequest;
import com.syuuk.patentflow.common.dto.CountryExtensionResponse;
import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.settings.dto.QuarterActivateResponse;
import com.syuuk.patentflow.settings.dto.QuarterSettingRequest;
import com.syuuk.patentflow.settings.dto.QuarterSettingResponse;
import com.syuuk.patentflow.settings.dto.ReviewScheduleRequest;
import com.syuuk.patentflow.settings.service.SettingsService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    public SettingsController(SettingsService settingsService, SystemSettingsService systemSettingsService) {
        this.settingsService = settingsService;
        this.systemSettingsService = systemSettingsService;
    }

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
            @RequestBody QuarterSettingRequest request) {
        return ApiResponse.ok(settingsService.updateQuarterSetting(quarterKey, request));
    }

    @GetMapping("/review-schedule")
    public ApiResponse<List<QuarterSettingResponse>> getReviewSchedule(
            @RequestParam(defaultValue = "0") int year) {
        int resolvedYear = year > 0 ? year : LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        return ApiResponse.ok(settingsService.getQuarterSettings(resolvedYear));
    }

    @PatchMapping("/review-schedule")
    public ApiResponse<List<QuarterSettingResponse>> updateReviewSchedule(
            @RequestBody ReviewScheduleRequest request) {
        int resolvedYear = request.year() > 0 ? request.year() : LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        return ApiResponse.ok(settingsService.updateReviewSchedule(
                resolvedYear,
                request.mailLeadMonths(),
                request.businessResponseDueDate()));
    }

    @PostMapping("/review-quarters/{quarterKey}/activate")
    public ApiResponse<QuarterActivateResponse> activateQuarter(@PathVariable String quarterKey) {
        return ApiResponse.ok(settingsService.activateQuarter(quarterKey));
    }

    @PostMapping("/review-quarters/{quarterKey}/end")
    public ApiResponse<QuarterSettingResponse> endQuarter(@PathVariable String quarterKey) {
        return ApiResponse.ok(settingsService.endQuarter(quarterKey));
    }

    @GetMapping("/country-extensions")
    public ApiResponse<List<CountryExtensionResponse>> getCountryExtensions() {
        return ApiResponse.ok(systemSettingsService.getCountryExtensions());
    }

    @PutMapping("/country-extensions/{country}")
    public ApiResponse<CountryExtensionResponse> updateCountryExtension(
            @PathVariable String country,
            @RequestBody CountryExtensionRequest request) {
        return ApiResponse.ok(systemSettingsService.updateCountryExtension(country, request));
    }

    @GetMapping("/classifications")
    public ApiResponse<List<ClassificationResponse>> getClassifications() {
        return ApiResponse.ok(systemSettingsService.getClassifications());
    }

    @PostMapping("/classifications/{type}")
    public ApiResponse<ClassificationResponse> addClassification(
            @PathVariable String type,
            @RequestBody ClassificationRequest request) {
        return ApiResponse.ok(systemSettingsService.addClassification(type, request.value()));
    }

    @PutMapping("/classifications/{type}/{value}")
    public ApiResponse<ClassificationResponse> renameClassification(
            @PathVariable String type,
            @PathVariable String value,
            @RequestBody ClassificationRequest request) {
        return ApiResponse.ok(systemSettingsService.renameClassification(type, value, request.value()));
    }

    @DeleteMapping("/classifications/{type}/{value}")
    public ApiResponse<ClassificationResponse> deleteClassification(
            @PathVariable String type,
            @PathVariable String value) {
        return ApiResponse.ok(systemSettingsService.deleteClassification(type, value));
    }
}
