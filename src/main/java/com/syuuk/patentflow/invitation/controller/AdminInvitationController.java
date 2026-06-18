/**
 * @author 유건욱
 * @date 2026-06-14
 */
package com.syuuk.patentflow.invitation.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.invitation.dto.BusinessInvitationStatusResponse;
import com.syuuk.patentflow.user.service.AdminUserService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @relatedFR FR-LEGAL-12, FR-LEGAL-23
 * @relatedUI UI-LEGAL-08
 * @description 관리자용 사업부 계정 초대/접근 상태 조회·재발송. 경로가 /api/v1/admin/** 라서
 *   SecurityConfig의 ADMIN 전용 가드가 그대로 적용된다(별도 시큐리티 변경 불필요).
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminInvitationController {

    private final AdminUserService adminUserService;

    public AdminInvitationController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/invitations")
    public ApiResponse<List<BusinessInvitationStatusResponse>> getInvitations() {
        return ApiResponse.ok(adminUserService.getBusinessInvitationStatuses());
    }

    @PostMapping("/users/{userId}/invitation/resend")
    public ApiResponse<BusinessInvitationStatusResponse> resendInvitation(@PathVariable String userId) {
        return ApiResponse.ok(adminUserService.resendInvitation(userId));
    }
}
