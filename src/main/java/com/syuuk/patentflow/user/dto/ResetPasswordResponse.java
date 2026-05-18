package com.syuuk.patentflow.user.dto;

public record ResetPasswordResponse(String userId, String username, String temporaryPassword) {}
