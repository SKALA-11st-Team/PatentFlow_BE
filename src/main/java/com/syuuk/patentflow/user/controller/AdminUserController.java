/**
 * @author 유건욱
 * @date 2026-05-19
 */
package com.syuuk.patentflow.user.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.user.dto.CreateUserRequest;
import com.syuuk.patentflow.common.response.PageResponse;
import com.syuuk.patentflow.user.dto.ResetPasswordResponse;
import com.syuuk.patentflow.user.dto.UserResponse;
import com.syuuk.patentflow.user.security.UserDetailsImpl;
import com.syuuk.patentflow.user.service.AdminUserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @relatedFR FR-COM-01, FR-LEGAL-25
 * @relatedUI UI-LEGAL-08
 * @description 관리자 사용자 관리 API. 관리자/법무/사업부 계정의 조회·생성·수정·삭제·비밀번호 초기화를 제공한다.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-LEGAL-08
     * @description 사용자 계정 목록을 조회한다. page/size/search가 모두 없으면 전체 목록, 있으면 검색·페이징 결과를 반환한다.
     */
    @GetMapping
    public ApiResponse<?> getUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search) {
        if (page == null && size == null && (search == null || search.isBlank())) {
            return ApiResponse.ok(adminUserService.getUsers());
        }
        PageResponse<UserResponse> users = adminUserService.getUsers(
                page != null ? page : 0,
                size != null ? size : 20,
                search);
        return ApiResponse.ok(users);
    }

    /**
     * @relatedFR FR-COM-01, FR-LEGAL-25
     * @relatedUI UI-LEGAL-08
     * @description 사용자 계정을 생성한다. 사업부 계정은 PENDING 상태로 만들고 초대 토큰과 초대 메일을 함께 발송한다.
     */
    @PostMapping
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(adminUserService.createUser(request));
    }

    /**
     * @relatedFR FR-COM-01, FR-LEGAL-25
     * @relatedUI UI-LEGAL-08
     * @description 사용자 계정의 이메일·역할·사업부·이름을 수정한다.
     */
    @PutMapping("/{userId}")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(adminUserService.updateUser(userId, request));
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-LEGAL-08
     * @description 사용자 계정을 삭제한다. 본인 계정·기본 관리자·마지막 관리자 계정은 삭제할 수 없다.
     */
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(
            @PathVariable String userId,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        adminUserService.deleteUser(userId, principal.getUser().getId());
        return ApiResponse.ok(null);
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-LEGAL-08
     * @description 사용자의 임시 비밀번호를 발급하고 안내 메일을 발송한다(레거시 경로, 신규 흐름은 초대 재발송 사용).
     */
    @PostMapping("/{userId}/reset-password")
    public ApiResponse<ResetPasswordResponse> resetPassword(@PathVariable String userId) {
        return ApiResponse.ok(adminUserService.resetPassword(userId));
    }
}
