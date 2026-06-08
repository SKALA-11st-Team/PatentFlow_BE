package com.syuuk.patentflow.user.dto;

public record ResetPasswordResponse(
        String userId,
        String email,
        String temporaryPassword,
        boolean emailSent,
        String message
) {}
