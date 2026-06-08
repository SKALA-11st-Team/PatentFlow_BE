package com.syuuk.patentflow.patent.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.auth.dto.UserPrincipalResponse;
import com.syuuk.patentflow.patent.dto.AnnualFeeScheduleAdjustmentRequest;
import com.syuuk.patentflow.patent.dto.AnnualFeeScheduleItemResponse;
import com.syuuk.patentflow.patent.service.AnnualFeeScheduleManagementService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/annual-fees/schedule")
public class AnnualFeeScheduleController {

    private final AnnualFeeScheduleManagementService service;

    public AnnualFeeScheduleController(AnnualFeeScheduleManagementService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AnnualFeeScheduleItemResponse>> getSchedule(
            @RequestParam(required = false) String country) {
        return ApiResponse.ok(service.getSchedule(country));
    }

    @PatchMapping("/{patentId}")
    public ApiResponse<AnnualFeeScheduleItemResponse> adjustSchedule(
            @PathVariable String patentId,
            @Valid @RequestBody AnnualFeeScheduleAdjustmentRequest request,
            Authentication authentication) {
        return ApiResponse.ok(service.adjustSchedule(patentId, request, currentAdjuster(authentication)));
    }

    private String currentAdjuster(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "관리자";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipalResponse userPrincipal) {
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
