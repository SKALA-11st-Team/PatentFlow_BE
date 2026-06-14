package com.syuuk.patentflow.invitation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @relatedFR FR-LEGAL-12
 * @description 초대 수락 요청 — 원문 토큰과 새 비밀번호.
 */
public record InvitationAcceptRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {}
