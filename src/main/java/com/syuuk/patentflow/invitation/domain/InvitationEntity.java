package com.syuuk.patentflow.invitation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * @relatedFR FR-LEGAL-12, FR-LEGAL-23
 * @description 사업부 계정 초대 토큰. 원문 토큰은 저장하지 않고 SHA-256 해시(tokenHash)만 보관하며,
 *   원문은 메일 링크에만 노출한다. responseDeadline은 발송 시점 회신 기한 스냅샷이다.
 */
@Entity
@Table(name = "invitations")
public class InvitationEntity {

    @Id
    @Column(length = 64)
    private String id; // "INV-" + UUID12

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "department_id", length = 64)
    private String departmentId;

    // 원문 토큰의 SHA-256 hex(64자). 원문은 저장 금지.
    @Column(name = "token_hash", length = 128, nullable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private InvitationStatus status;

    // 발송 시점의 회신 기한 스냅샷(분기 submissionDeadline). 없을 수 있다.
    @Column(name = "response_deadline")
    private LocalDate responseDeadline;

    @Column(name = "invited_at")
    private OffsetDateTime invitedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    protected InvitationEntity() {
    }

    public InvitationEntity(String id, String userId, String departmentId, String tokenHash,
            InvitationStatus status, LocalDate responseDeadline,
            OffsetDateTime invitedAt, OffsetDateTime expiresAt) {
        this.id = id;
        this.userId = userId;
        this.departmentId = departmentId;
        this.tokenHash = tokenHash;
        this.status = status;
        this.responseDeadline = responseDeadline;
        this.invitedAt = invitedAt;
        this.expiresAt = expiresAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getDepartmentId() { return departmentId; }
    public String getTokenHash() { return tokenHash; }
    public InvitationStatus getStatus() { return status; }
    public void setStatus(InvitationStatus status) { this.status = status; }
    public LocalDate getResponseDeadline() { return responseDeadline; }
    public OffsetDateTime getInvitedAt() { return invitedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(OffsetDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
}
