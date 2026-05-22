package com.syuuk.patentflow.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateDepartmentRequest(
        @NotBlank String departmentName
) {
}
