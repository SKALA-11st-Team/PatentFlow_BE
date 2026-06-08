package com.syuuk.patentflow.user.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.user.dto.CreateUserRequest;
import com.syuuk.patentflow.user.dto.PageResponse;
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

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

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

    @PostMapping
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(adminUserService.createUser(request));
    }

    @PutMapping("/{userId}")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(adminUserService.updateUser(userId, request));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(
            @PathVariable String userId,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        adminUserService.deleteUser(userId, principal.getUser().getId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{userId}/reset-password")
    public ApiResponse<ResetPasswordResponse> resetPassword(@PathVariable String userId) {
        return ApiResponse.ok(adminUserService.resetPassword(userId));
    }
}
