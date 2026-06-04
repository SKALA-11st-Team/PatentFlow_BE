package com.syuuk.patentflow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        // 최소 길이 4 — 너무 짧은 비밀번호 방지
        @NotBlank @Size(min = 4) String newPassword
) {}
