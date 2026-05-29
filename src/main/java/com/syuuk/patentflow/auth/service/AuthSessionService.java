package com.syuuk.patentflow.auth.service;

import com.syuuk.patentflow.auth.config.AuthProperties;
import com.syuuk.patentflow.auth.domain.AuthSessionEntity;
import com.syuuk.patentflow.auth.repository.AuthSessionRepository;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.user.security.UserDetailsImpl;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthSessionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthProperties properties;
    private final AuthSessionRepository authSessionRepository;

    public AuthSessionService(AuthProperties properties, AuthSessionRepository authSessionRepository) {
        this.properties = properties;
        this.authSessionRepository = authSessionRepository;
    }

    @Transactional
    public RefreshSession create(UserDetailsImpl userDetails) {
        byte[] randomBytes = new byte[48];
        SECURE_RANDOM.nextBytes(randomBytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        OffsetDateTime now = OffsetDateTime.now(KST);
        OffsetDateTime expiresAt = now.plusSeconds(properties.getRefreshTokenExpirationSeconds());
        AuthSessionEntity session = new AuthSessionEntity(
                UUID.randomUUID().toString(),
                userDetails.getUser().getId(),
                userDetails.getUsername(),
                // refresh_token 원문은 저장하지 않고 해시만 보관 — DB 유출 시 토큰 재사용 방지
                hash(refreshToken),
                now,
                expiresAt);
        authSessionRepository.save(session);
        // 만료 후 하루가 지난 세션을 정리 — 즉시 삭제하지 않고 하루 버퍼를 두어 클럭 오차 대응
        authSessionRepository.deleteByExpiresAtBefore(now.minusDays(1));
        return new RefreshSession(refreshToken, expiresAt.toInstant());
    }

    @Transactional
    public UserSession consume(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new PatentFlowException(ErrorCode.UNAUTHORIZED);
        }
        AuthSessionEntity session = authSessionRepository.findByRefreshTokenHash(hash(refreshToken))
                .orElseThrow(() -> new PatentFlowException(ErrorCode.UNAUTHORIZED));
        OffsetDateTime now = OffsetDateTime.now(KST);
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)) {
            throw new PatentFlowException(ErrorCode.UNAUTHORIZED);
        }
        session.markUsed(now);
        // refresh_token은 1회성 — 사용 즉시 revoke해 재사용 공격(replay attack) 방지
        session.revoke(now);
        authSessionRepository.save(session);
        return new UserSession(session.getEmail());
    }

    @Transactional
    public void revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        authSessionRepository.findByRefreshTokenHash(hash(refreshToken))
                .filter(session -> session.getRevokedAt() == null)
                .ifPresent(session -> {
                    session.revoke(OffsetDateTime.now(KST));
                    authSessionRepository.save(session);
                });
    }

    // 비밀번호 변경 후 해당 사용자의 모든 세션을 무효화해 재로그인을 강제한다.
    @Transactional
    public void revokeAll(String userId) {
        OffsetDateTime now = OffsetDateTime.now(KST);
        authSessionRepository.findByUserId(userId).forEach(session -> {
            if (session.getRevokedAt() == null) {
                session.revoke(now);
                authSessionRepository.save(session);
            }
        });
    }

    private String hash(String token) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Refresh token hash failed.", exception);
        }
        return HexFormat.of().formatHex(digest);
    }

    public record RefreshSession(String refreshToken, Instant expiresAt) {}

    public record UserSession(String email) {}
}
