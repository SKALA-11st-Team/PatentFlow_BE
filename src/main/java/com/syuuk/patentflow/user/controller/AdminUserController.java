package com.syuuk.patentflow.user.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.user.dto.CreateUserRequest;
import com.syuuk.patentflow.user.dto.ResetPasswordResponse;
import com.syuuk.patentflow.user.dto.UserResponse;
import com.syuuk.patentflow.user.service.AdminUserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    public ApiResponse<List<UserResponse>> getUsers() {
        return ApiResponse.ok(adminUserService.getUsers());
    }

    @PostMapping
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(adminUserService.createUser(request));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable String userId) {
        adminUserService.deleteUser(userId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{userId}/reset-password")
    public ApiResponse<ResetPasswordResponse> resetPassword(@PathVariable String userId) {
        return ApiResponse.ok(adminUserService.resetPassword(userId));
    }
}
