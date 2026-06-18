/**
 * @author 유건욱
 * @date 2026-05-22
 */
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @relatedFR FR-LEGAL-24
 * @relatedUI UI-LEGAL-01, UI-LEGAL-02, UI-LEGAL-07
 * @description 국가별 연차료 스케줄 조회·조정·재계산 API. 미래 연차료 납부 예정일을 국가 규칙에 따라 제공/조정한다.
 */
@RestController
@RequestMapping("/api/v1/annual-fees/schedule")
public class AnnualFeeScheduleController {

    private final AnnualFeeScheduleManagementService service;

    public AnnualFeeScheduleController(AnnualFeeScheduleManagementService service) {
        this.service = service;
    }

    /**
     * @relatedFR FR-LEGAL-24
     * @relatedUI UI-LEGAL-01, UI-LEGAL-02
     * @description 국가(선택)별로 특허들의 연차료 납부 예정일 스케줄을 조회한다.
     */
    @GetMapping
    public ApiResponse<List<AnnualFeeScheduleItemResponse>> getSchedule(
            @RequestParam(required = false) String country) {
        return ApiResponse.ok(service.getSchedule(country));
    }

    /**
     * @relatedFR FR-LEGAL-24
     * @relatedUI UI-LEGAL-07
     * @description 특정 특허의 연차료 납부 예정일을 관리자가 조정하고(조정 사유·이력 포함) 조정 결과를 반환한다.
     */
    @PatchMapping("/{patentId}")
    public ApiResponse<AnnualFeeScheduleItemResponse> adjustSchedule(
            @PathVariable String patentId,
            @Valid @RequestBody AnnualFeeScheduleAdjustmentRequest request,
            Authentication authentication) {
        return ApiResponse.ok(service.adjustSchedule(patentId, request, currentAdjuster(authentication)));
    }

    /**
     * @relatedFR FR-LEGAL-24
     * @relatedUI UI-LEGAL-07
     * @description FEE-05: 저장된 과거 납부일을 국가 연장 개월 단위로 미래까지 전진 저장하고 전진된 특허 수를 반환한다.
     */
    @PostMapping("/recompute")
    public ApiResponse<Integer> recompute() {
        return ApiResponse.ok(service.recomputeOverdueSchedules());
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
