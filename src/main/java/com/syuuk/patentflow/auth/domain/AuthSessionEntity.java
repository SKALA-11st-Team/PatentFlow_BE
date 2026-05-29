package com.syuuk.patentflow.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

@Entity
@Table(name = "auth_sessions", uniqueConstraints = @UniqueConstraint(columnNames = "refresh_token_hash"))
public class AuthSessionEntity {

    @Id
    @Column(length = 64)
    private String sessionId;

    @Column(nullable = false, length = 64)
    private String userId;

    // 세션 생성 시점의 로그인 ID(이메일) 스냅샷 — users.email과 같은 값
    @Column(nullable = false, length = 256)
    private String email;

    @Column(name = "refresh_token_hash", nullable = false, length = 128)
    private String refreshTokenHash;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @Column
    private OffsetDateTime lastUsedAt;

    @Column
    private OffsetDateTime revokedAt;

    protected AuthSessionEntity() {}

    public AuthSessionEntity(
            String sessionId,
            String userId,
            String email,
            String refreshTokenHash,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt
    ) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.email = email;
        this.refreshTokenHash = refreshTokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastUsedAt = createdAt;
    }

    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }

    public void markUsed(OffsetDateTime usedAt) { this.lastUsedAt = usedAt; }
    public void revoke(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }
}
