/**
 * @author 유건욱
 * @date 2026-06-14
 */
package com.syuuk.patentflow.invitation.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.invitation.domain.InvitationEntity;
import com.syuuk.patentflow.invitation.domain.InvitationStatus;
import com.syuuk.patentflow.invitation.repository.InvitationRepository;
import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @relatedFR FR-LEGAL-12, FR-LEGAL-23
 * @description 사업부 계정 초대 토큰 생성/회전/수락/검증. 원문 토큰은 저장하지 않고 SHA-256 해시만
 *   보관하며 원문은 호출부(메일 발송)에만 반환한다. 만료는 스케줄러 없이 조회/검증 시점 lazy 판정한다.
 *   만료 전이의 영속화는 read-write 트랜잭션(accept/validate)에서 managed 엔티티 dirty checking으로 이뤄지고,
 *   read-only 목록 조회 경로에서는 표시 전용 in-memory 전이로만 동작한다.
 */
@Service
public class InvitationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256-bit URL-safe 토큰

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final SystemSettingsService systemSettingsService;
    private final PasswordEncoder passwordEncoder;

    public InvitationService(InvitationRepository invitationRepository,
            UserRepository userRepository,
            SystemSettingsService systemSettingsService,
            PasswordEncoder passwordEncoder) {
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.systemSettingsService = systemSettingsService;
        this.passwordEncoder = passwordEncoder;
    }

    /** 생성된 초대와 원문 토큰(메일 링크용). 원문은 절대 저장하지 않으므로 이 반환값에서만 얻을 수 있다. */
    public record CreatedInvitation(InvitationEntity invitation, String rawToken) {}

    /**
     * 새 초대를 생성한다. 기존 PENDING 초대는 REVOKED 처리(rotate)한다.
     * responseDeadline은 발송 시점 회신 기한 스냅샷(없으면 null).
     */
    @Transactional
    public CreatedInvitation createInvitation(UserEntity user, LocalDate responseDeadline) {
        revokePending(user.getId());
        String rawToken = generateToken();
        OffsetDateTime now = OffsetDateTime.now(KST);
        OffsetDateTime expiresAt = now.plusDays(systemSettingsService.getInvitationTtlDays());
        InvitationEntity invitation = new InvitationEntity(
                "INV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(),
                user.getId(),
                user.getDepartmentId(),
                sha256Hex(rawToken),
                InvitationStatus.PENDING,
                responseDeadline,
                now,
                expiresAt);
        invitationRepository.save(invitation);
        return new CreatedInvitation(invitation, rawToken);
    }

    /**
     * 토큰 수락: 해시로 조회 → PENDING·미만료 검증 → BCrypt 비밀번호 설정,
     * invitation ACCEPTED/acceptedAt, user.status ACTIVE.
     *
     * <p>순차 재수락은 PENDING 검사에서 invalidStateException(ACCEPTED)으로 멱등 거절된다.
     * 단, 동일 유효 토큰의 '거의 동시' 두 요청은 둘 다 PENDING 검사를 통과해 마지막 커밋이
     * 비밀번호를 덮어쓸 수 있다 — 완전한 직렬화는 InvitationEntity의 @Version(낙관적 락) 또는
     * findByTokenHash의 @Lock(PESSIMISTIC_WRITE)이 필요하며, 둘 다 이 서비스 밖(엔티티/리포지토리)
     * 변경이라 여기서는 다루지 않는다. 빈도는 동일 사용자 본인의 중복 제출 + 레이트리밋으로 제한된다.
     */
    @Transactional
    public void accept(String rawToken, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "새 비밀번호를 입력해 주세요.");
        }
        InvitationEntity invitation = invitationRepository.findByTokenHashForUpdate(sha256Hex(rawToken))
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST, "유효하지 않은 초대 링크입니다."));
        InvitationStatus effective = lazyExpire(invitation);
        if (effective != InvitationStatus.PENDING) {
            throw invalidStateException(effective);
        }
        UserEntity user = userRepository.findById(invitation.getUserId())
                .orElseThrow(() -> new PatentFlowException(ErrorCode.USER_NOT_FOUND));
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setStatus("ACTIVE");
        userRepository.save(user);
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(OffsetDateTime.now(KST));
        invitationRepository.save(invitation);
    }

    /** 수락 화면용 토큰 검증. 유효성/만료/이메일을 반환한다(만료/무효도 valid=false로 표현, 이메일은 가능 시 노출). */
    @Transactional
    public InvitationValidation validate(String rawToken) {
        InvitationEntity invitation = invitationRepository.findByTokenHash(sha256Hex(rawToken)).orElse(null);
        if (invitation == null) {
            return new InvitationValidation(false, InvitationStatus.REVOKED, null, null);
        }
        InvitationStatus effective = lazyExpire(invitation);
        UserEntity user = userRepository.findById(invitation.getUserId()).orElse(null);
        String email = user != null ? user.getEmail() : null;
        boolean valid = effective == InvitationStatus.PENDING;
        return new InvitationValidation(valid, effective, email, invitation.getResponseDeadline());
    }

    public record InvitationValidation(boolean valid, InvitationStatus status, String email,
            LocalDate responseDeadline) {}

    /** 사용자별 현재(최신) 초대 — 없으면 null. 조회 시점에 만료 lazy 판정을 반영한다. */
    @Transactional
    public InvitationEntity currentInvitation(String userId) {
        InvitationEntity invitation = invitationRepository.findFirstByUserIdOrderByInvitedAtDesc(userId).orElse(null);
        if (invitation != null) {
            lazyExpire(invitation);
        }
        return invitation;
    }

    /** 사용자의 PENDING 초대를 모두 REVOKED 처리(rotate). */
    @Transactional
    public void revokePending(String userId) {
        List<InvitationEntity> pendings = invitationRepository.findByUserIdAndStatus(userId, InvitationStatus.PENDING);
        for (InvitationEntity inv : pendings) {
            inv.setStatus(InvitationStatus.REVOKED);
        }
        if (!pendings.isEmpty()) {
            invitationRepository.saveAll(pendings);
        }
    }

    /**
     * 만료 lazy 판정: PENDING이고 now>expiresAt면 in-memory로 EXPIRED 전이 후 반환. 그 외는 현재 상태 그대로.
     * 영속화는 명시적 save가 아니라 managed 엔티티의 dirty checking에 맡긴다 — accept/validate 같은
     * read-write 트랜잭션에서는 커밋 시 자동 flush되어 EXPIRED가 DB에 반영되고, 목록 조회 같은
     * read-only 트랜잭션(getBusinessInvitationStatuses)에서는 flush가 일어나지 않아 표시 전용으로만 쓰인다.
     * (read-only 트랜잭션에 합류하면 어차피 명시적 save도 flush되지 않으므로, 의도와 어긋나는 no-op save를 두지 않는다.)
     */
    private InvitationStatus lazyExpire(InvitationEntity invitation) {
        if (invitation.getStatus() == InvitationStatus.PENDING
                && invitation.getExpiresAt() != null
                && OffsetDateTime.now(KST).isAfter(invitation.getExpiresAt())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
        }
        return invitation.getStatus();
    }

    private PatentFlowException invalidStateException(InvitationStatus status) {
        return switch (status) {
            case EXPIRED -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "초대 링크가 만료되었습니다. 관리자에게 재발송을 요청해 주세요.");
            case ACCEPTED -> new PatentFlowException(ErrorCode.INVALID_REQUEST, "이미 수락된 초대입니다.");
            case REVOKED -> new PatentFlowException(ErrorCode.INVALID_REQUEST, "유효하지 않은 초대 링크입니다.");
            default -> new PatentFlowException(ErrorCode.INVALID_REQUEST, "유효하지 않은 초대 링크입니다.");
        };
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new PatentFlowException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
