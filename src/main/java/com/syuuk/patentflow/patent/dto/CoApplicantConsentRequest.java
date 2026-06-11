package com.syuuk.patentflow.patent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 공동출원인 합의 기록 요청. 합의 상태(AGREED/DISAGREED)와 사유를 받는다.
 */
public record CoApplicantConsentRequest(
        @NotNull CoApplicantConsentStatus status,
        @NotBlank String reason
) {
}
