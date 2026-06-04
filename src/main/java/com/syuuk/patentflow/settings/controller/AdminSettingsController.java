package com.syuuk.patentflow.settings.controller;

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

    /**
     * @description 관리자 설정 화면에서 Gmail 발송 설정을 조회한다.
     */
    @GetMapping("/mail")
    public ApiResponse<MailSettingsResponse> getMailSettings() {
        return ApiResponse.ok(systemSettingsService.getMailSettings());
    }

    /**
     * @description 관리자 설정 화면에서 Gmail 계정/앱 비밀번호 기반 발송 설정을 저장한다.
     */
    @PutMapping("/mail")
    public ApiResponse<MailSettingsResponse> updateMailSettings(@RequestBody MailSettingsRequest request) {
        return ApiResponse.ok(systemSettingsService.saveMailSettings(request));
    }
}
