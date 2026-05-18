package com.syuuk.patentflow.user.dto;

import com.syuuk.patentflow.user.domain.UserEntity;
import java.time.OffsetDateTime;

public record UserResponse(
        String id,
        String username,
        String role,
        String departmentId,
        String departmentName,
        String displayName,
        OffsetDateTime createdAt
) {
    public static UserResponse from(UserEntity u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getRole(),
                u.getDepartmentId(), u.getDepartmentName(), u.getDisplayName(), u.getCreatedAt());
    }
}
