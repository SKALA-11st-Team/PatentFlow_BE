package com.syuuk.patentflow.user.dto;

import com.syuuk.patentflow.user.domain.UserEntity;
import java.time.OffsetDateTime;

public record UserResponse(
        String id,
        String email,          // 로그인 ID
        String username,       // 실제 이름
        String role,
        String departmentId,
        String departmentName,
        OffsetDateTime createdAt
) {
    public static UserResponse from(UserEntity u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getUsername(), u.getRole(),
                u.getDepartmentId(), u.getDepartmentName(), u.getCreatedAt());
    }
}
