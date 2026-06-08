package com.syuuk.patentflow.auth.repository;

import com.syuuk.patentflow.auth.domain.AuthSessionEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, String> {

    Optional<AuthSessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthSessionEntity session
               set session.revokedAt = :revokedAt
             where session.userId = :userId
               and session.revokedAt is null
            """)
    int revokeActiveByUserId(@Param("userId") String userId, @Param("revokedAt") OffsetDateTime revokedAt);

    void deleteByExpiresAtBefore(OffsetDateTime expiresAt);
}
