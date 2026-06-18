/**
 * @author 유건욱
 * @date 2026-06-14
 */
package com.syuuk.patentflow.invitation.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * @relatedFR FR-LEGAL-12, FR-LEGAL-23
 * @relatedUI UI-LEGAL-08
 * @description 사업부(BUSINESS) 계정별 초대/접근 상태. FE BusinessInvitationStatus 타입과 필드명이 정확히 일치한다.
 */
public record BusinessInvitationStatusResponse(
        String userId,
        String email,
        String username,
        String departmentId,
        String departmentName,
        String accountStatus,        // ACTIVE | PENDING | INACTIVE
        String invitationStatus,     // PENDING | ACCEPTED | EXPIRED | REVOKED
        LocalDate responseDeadline,  // 발송 시점 회신 기한 스냅샷(없으면 null)
        OffsetDateTime invitedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime acceptedAt,
        OffsetDateTime lastAccessAt
) {}
