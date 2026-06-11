package com.syuuk.patentflow.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateUserRequest(
        @NotBlank @Email String email,         // 로그인 ID — 이메일 형식 필수
        @NotBlank @Pattern(regexp = "ADMIN|LEGAL|BUSINESS") String role,
        String departmentId,
        @NotBlank String username              // 실제 이름 (예: 이소율)
) {}
