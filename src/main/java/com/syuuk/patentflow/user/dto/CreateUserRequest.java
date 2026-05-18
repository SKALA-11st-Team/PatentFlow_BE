package com.syuuk.patentflow.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateUserRequest(
        @NotBlank @Email String username,
        @NotBlank @Pattern(regexp = "ADMIN|BUSINESS") String role,
        String departmentId,
        @NotBlank String displayName
) {}
