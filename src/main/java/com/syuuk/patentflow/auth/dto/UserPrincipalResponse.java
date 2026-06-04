package com.syuuk.patentflow.auth.dto;

import java.util.List;

public record UserPrincipalResponse(
        String email,          // 로그인 ID
        String username,       // 실제 이름
        List<String> roles,
        String userId,
        String role,
        String departmentId,
        String departmentName
) {}
