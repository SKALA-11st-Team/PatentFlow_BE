/**
 * @author 유건욱
 * @date 2026-06-14
 */
package com.syuuk.patentflow.invitation.dto;

import java.time.LocalDate;

/**
 * @relatedFR FR-LEGAL-12
 * @relatedUI UI-LEGAL-08
 * @description 공개 초대 토큰 검증 결과(수락 화면용). 만료/무효 시 valid=false, 가능하면 이메일을 노출한다.
 */
public record InvitationValidationResponse(
        boolean valid,
        String status,               // PENDING | ACCEPTED | EXPIRED | REVOKED
        String email,
        LocalDate responseDeadline
) {}
