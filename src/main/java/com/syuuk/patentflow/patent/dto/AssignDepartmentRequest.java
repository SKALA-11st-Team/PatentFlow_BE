package com.syuuk.patentflow.patent.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignDepartmentRequest(
        @NotBlank String departmentId
) {
}
