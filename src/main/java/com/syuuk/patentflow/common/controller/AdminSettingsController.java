package com.syuuk.patentflow.common.controller;

import com.syuuk.patentflow.common.dto.MailSettingsRequest;
import com.syuuk.patentflow.common.dto.MailSettingsResponse;
import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
public class AdminSettingsController {

    private final SystemSettingsService systemSettingsService;

    public AdminSettingsController(SystemSettingsService systemSettingsService) {
        this.systemSettingsService = systemSettingsService;
    }

    @GetMapping("/mail")
    public ApiResponse<MailSettingsResponse> getMailSettings() {
        return ApiResponse.ok(systemSettingsService.getMailSettings());
    }

    @PutMapping("/mail")
    public ApiResponse<MailSettingsResponse> updateMailSettings(@RequestBody MailSettingsRequest request) {
        return ApiResponse.ok(systemSettingsService.saveMailSettings(request));
    }
}
