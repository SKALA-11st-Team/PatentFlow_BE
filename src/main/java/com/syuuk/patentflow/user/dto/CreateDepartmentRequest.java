package com.syuuk.patentflow.user.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDepartmentRequest(
        @NotBlank String departmentId,
        @NotBlank String departmentName
) {
}
