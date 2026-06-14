package com.syuuk.patentflow.invitation.repository;

import com.syuuk.patentflow.invitation.domain.InvitationEntity;
import com.syuuk.patentflow.invitation.domain.InvitationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<InvitationEntity, String> {

    List<InvitationEntity> findByUserId(String userId);

    List<InvitationEntity> findByUserIdAndStatus(String userId, InvitationStatus status);

    Optional<InvitationEntity> findByTokenHash(String tokenHash);

    // 사용자별 최신 초대 1건(목록 화면의 현재 상태 표시용).
    Optional<InvitationEntity> findFirstByUserIdOrderByInvitedAtDesc(String userId);
}
