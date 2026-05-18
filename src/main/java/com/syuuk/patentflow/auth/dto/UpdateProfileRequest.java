package com.syuuk.patentflow.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank String displayName
) {
}
