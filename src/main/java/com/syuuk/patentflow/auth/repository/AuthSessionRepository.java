package com.syuuk.patentflow.auth.repository;

import com.syuuk.patentflow.auth.domain.AuthSessionEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, String> {

    Optional<AuthSessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    List<AuthSessionEntity> findByUserId(String userId);

    void deleteByExpiresAtBefore(OffsetDateTime expiresAt);
}
