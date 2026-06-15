package com.syuuk.patentflow.invitation.repository;

import com.syuuk.patentflow.invitation.domain.InvitationEntity;
import com.syuuk.patentflow.invitation.domain.InvitationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationRepository extends JpaRepository<InvitationEntity, String> {

    List<InvitationEntity> findByUserId(String userId);

    List<InvitationEntity> findByUserIdAndStatus(String userId, InvitationStatus status);

    Optional<InvitationEntity> findByTokenHash(String tokenHash);

    /** 초대 수락 동시성 보호 — 같은 토큰의 near-동시 수락이 직렬화되도록 비관적 쓰기 락으로 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InvitationEntity i where i.tokenHash = :tokenHash")
    Optional<InvitationEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    // 사용자별 최신 초대 1건(목록 화면의 현재 상태 표시용).
    Optional<InvitationEntity> findFirstByUserIdOrderByInvitedAtDesc(String userId);
}
