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
                hash(refreshToken),
                now,
                expiresAt);
        authSessionRepository.save(session);
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
        session.revoke(now);
        authSessionRepository.save(session);
        return new UserSession(session.getUsername());
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

    public record UserSession(String username) {}
}
