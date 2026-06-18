/**
 * @author 유건욱
 * @date 2026-06-15
 */
package com.syuuk.patentflow.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.invitation.domain.InvitationEntity;
import com.syuuk.patentflow.invitation.domain.InvitationStatus;
import com.syuuk.patentflow.invitation.repository.InvitationRepository;
import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * @relatedFR FR-LEGAL-12, FR-LEGAL-23
 * 만료 lazy 판정(be-invitation-3) 회귀 테스트: 목록 조회 경로(currentInvitation)는 read-only 트랜잭션에서
 * 합류하므로 명시적 save를 하지 않고 표시 전용 in-memory 전이만 반환한다. 영속화는 read-write 경로의
 * managed 엔티티 dirty checking에 맡긴다(이 단위 테스트에서는 in-memory 전이만 검증).
 */
class InvitationServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private InvitationRepository invitationRepository;
    private UserRepository userRepository;
    private SystemSettingsService systemSettingsService;
    private PasswordEncoder passwordEncoder;
    private InvitationService service;

    @BeforeEach
    void setUp() {
        invitationRepository = mock(InvitationRepository.class);
        userRepository = mock(UserRepository.class);
        systemSettingsService = mock(SystemSettingsService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new InvitationService(
                invitationRepository, userRepository, systemSettingsService, passwordEncoder);
    }

    private InvitationEntity pendingInvitation(String userId, OffsetDateTime expiresAt) {
        OffsetDateTime now = OffsetDateTime.now(KST);
        return new InvitationEntity(
                "INV-TEST00000001", userId, "DEPT-1", "hash",
                InvitationStatus.PENDING, null, now.minusDays(1), expiresAt);
    }

    @Test
    void currentInvitation_expiredPending_transitionsInMemoryWithoutExplicitSave() {
        OffsetDateTime expiredAt = OffsetDateTime.now(KST).minusDays(1);
        InvitationEntity invitation = pendingInvitation("USR-1", expiredAt);
        when(invitationRepository.findFirstByUserIdOrderByInvitedAtDesc("USR-1"))
                .thenReturn(Optional.of(invitation));

        InvitationEntity result = service.currentInvitation("USR-1");

        // 표시 전용 in-memory 전이는 일어나야 한다.
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
        // read-only 합류 경로에서 의도와 어긋나는 no-op save를 호출하지 않는다.
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void currentInvitation_pendingNotExpired_keepsPendingAndDoesNotSave() {
        OffsetDateTime futureExpiry = OffsetDateTime.now(KST).plusDays(3);
        InvitationEntity invitation = pendingInvitation("USR-2", futureExpiry);
        when(invitationRepository.findFirstByUserIdOrderByInvitedAtDesc("USR-2"))
                .thenReturn(Optional.of(invitation));

        InvitationEntity result = service.currentInvitation("USR-2");

        assertThat(result.getStatus()).isEqualTo(InvitationStatus.PENDING);
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void accept_expiredPending_rejectsWithInvalidState() {
        OffsetDateTime expiredAt = OffsetDateTime.now(KST).minusDays(1);
        InvitationEntity invitation = pendingInvitation("USR-3", expiredAt);
        when(invitationRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(invitation));

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> service.accept("raw-token", "newPassword1!"))
                .isInstanceOf(com.syuuk.patentflow.common.error.PatentFlowException.class);
        // 만료 전이는 in-memory로 반영되되 user는 갱신되지 않는다.
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
        verify(userRepository, never()).save(any());
    }

    @Test
    void accept_validPending_setsPasswordAndAccepted() {
        OffsetDateTime futureExpiry = OffsetDateTime.now(KST).plusDays(3);
        InvitationEntity invitation = pendingInvitation("USR-4", futureExpiry);
        UserEntity user = new UserEntity("USR-4", "biz@patentflow.live", "old", "BUSINESS", "DEPT-1", "biz");
        when(invitationRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(invitation));
        when(userRepository.findById("USR-4")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword1!")).thenReturn("encoded");

        service.accept("raw-token", "newPassword1!");

        assertThat(user.getPassword()).isEqualTo("encoded");
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitation.getAcceptedAt()).isNotNull();
    }
}
