/**
 * @author 유건욱
 * @date 2026-05-19
 */
package com.syuuk.patentflow.user.service;

import com.syuuk.patentflow.auth.service.AuthSessionService;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.invitation.domain.InvitationEntity;
import com.syuuk.patentflow.invitation.dto.BusinessInvitationStatusResponse;
import com.syuuk.patentflow.invitation.service.InvitationService;
import com.syuuk.patentflow.mailing.repository.DepartmentRepository;
import com.syuuk.patentflow.mailing.service.MailOAuth2Service;
import com.syuuk.patentflow.settings.dto.QuarterSettingResponse;
import com.syuuk.patentflow.settings.service.SettingsService;
import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.dto.CreateUserRequest;
import com.syuuk.patentflow.common.response.PageInfo;
import com.syuuk.patentflow.common.response.PageResponse;
import com.syuuk.patentflow.user.dto.ResetPasswordResponse;
import com.syuuk.patentflow.user.dto.UserResponse;
import com.syuuk.patentflow.user.repository.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @relatedFR FR-COM-01, FR-LEGAL-25
 * @relatedUI UI-LEGAL-08
 * @description 사용자 계정 관리 서비스. 계정 조회·생성(초대 메일)·수정·삭제·비밀번호 초기화와 사업부 초대 상태를 다룬다.
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$";
    private static final String ROLE_ADMIN = "ADMIN";
    // I3: LEGAL — 검토 업무(특허·연차료·메일)는 ADMIN과 동일, 운영(계정·설정)만 제한되는 역할.
    private static final String ROLE_LEGAL = "LEGAL";
    private static final String ROLE_BUSINESS = "BUSINESS";
    private static final Set<String> PROTECTED_ADMIN_IDS = Set.of("USER-admin", "USER-admin-bootstrap");

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemSettingsService systemSettingsService;
    private final MailOAuth2Service mailOAuth2Service;
    private final InvitationService invitationService;
    private final SettingsService settingsService;
    private final AuthSessionService authSessionService;
    // 초대 수락 링크 베이스 URL(FE 도메인). 미설정 시 상대경로 폴백.
    @Value("${patentflow.invite.base-url:}")
    private String inviteBaseUrl;

    public AdminUserService(UserRepository userRepository, DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder,
            SystemSettingsService systemSettingsService, MailOAuth2Service mailOAuth2Service,
            InvitationService invitationService, SettingsService settingsService,
            AuthSessionService authSessionService) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.systemSettingsService = systemSettingsService;
        this.mailOAuth2Service = mailOAuth2Service;
        this.invitationService = invitationService;
        this.settingsService = settingsService;
        this.authSessionService = authSessionService;
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-LEGAL-08
     * @description 전체 사용자 계정을 생성일 순으로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getUsers() {
        return userRepository.findAll(Sort.by("createdAt")).stream()
                .map(UserResponse::from)
                .toList();
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-LEGAL-08
     * @description 이메일·계정명·사업부 ID 검색어로 사용자 계정을 검색·페이징 조회한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(int page, int size, String search) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("createdAt"));
        Page<UserEntity> users = isBlank(search)
                ? userRepository.findAll(pageable)
                : userRepository.findByEmailContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrDepartmentIdContainingIgnoreCase(
                        search.trim(), search.trim(), search.trim(), pageable);
        return PageResponse.ok(users.map(UserResponse::from).getContent(),
                new PageInfo(users.getNumber(), users.getSize(), users.getTotalElements(), users.getTotalPages()));
    }

    /**
     * @relatedFR FR-COM-01, FR-LEGAL-25
     * @relatedUI UI-LEGAL-08
     * @description 계정을 생성한다. 사업부 계정은 PENDING + 임시 비밀번호로 만들고 초대 토큰·초대 메일을 발송한다(메일 실패는 롤백하지 않음).
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        validateAdminRoleChange(null, null, request.role());
        String departmentId = validateDepartmentId(request.role(), request.departmentId());
        if (userRepository.existsByEmail(request.email())) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "이미 존재하는 계정입니다: " + request.email());
        }
        // 임시 비밀번호로 자리만 채우고(초대 수락 시 사용자가 직접 설정), 계정은 PENDING으로 둔다.
        String tempPassword = generatePassword();
        String id = "USER-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        UserEntity user = new UserEntity(id, request.email(),
                passwordEncoder.encode(tempPassword),
                request.role(), departmentId, request.username());
        user.setStatus("PENDING");
        userRepository.save(user);
        // 초대 토큰 생성 + 초대 메일 발송. USER-06: 메일 실패가 계정/초대 생성을 롤백시키지 않는다.
        LocalDate responseDeadline = activeResponseDeadline();
        InvitationService.CreatedInvitation created = invitationService.createInvitation(user, responseDeadline);
        try {
            sendInvitationEmail(user.getEmail(), user.getUsername(), created.rawToken(), responseDeadline);
        } catch (RuntimeException mailError) {
            log.warn("계정·초대는 생성됐으나 초대 메일 발송 실패: recipient={} — {}", user.getEmail(), mailError.getMessage());
        }
        // department 연관은 insertable=false라 신규 엔티티에서 null → 응답의 사업부명이 공란이 되는 문제를
        // 막기 위해 검증된 departmentId로 사업부명을 직접 조회해 응답에 주입한다.
        return userResponseWithDepartmentName(user, departmentId);
    }

    /**
     * @relatedFR FR-COM-01, FR-LEGAL-12, FR-LEGAL-23
     * @relatedUI UI-LEGAL-08
     * @description 초대 재발송: 사용자의 PENDING 초대를 rotate 후 새 초대 생성 + 메일 발송.
     * USER-06: 메일 실패가 초대 rotate/생성을 롤백시키지 않는다(커밋된 새 초대는 보존).
     */
    @Transactional
    public BusinessInvitationStatusResponse resendInvitation(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "사용자를 찾을 수 없습니다: " + userId));
        if (!ROLE_BUSINESS.equals(user.getRole())) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "사업부 계정에만 초대를 발송할 수 있습니다.");
        }
        // 이미 수락한 계정은 다시 PENDING으로 되돌려 재초대한다(접근 회수 후 재온보딩 시나리오).
        // 재초대 = 접근 회수이므로 발급된 refresh 세션을 즉시 무효화한다(changePassword와 동일 처리).
        // status를 PENDING으로만 바꾸면 보안 계층은 status를 보지 않아 접근이 그대로 유지된다 — revokeAll로 회수.
        user.setStatus("PENDING");
        userRepository.save(user);
        authSessionService.revokeAll(userId);
        LocalDate responseDeadline = activeResponseDeadline();
        InvitationService.CreatedInvitation created = invitationService.createInvitation(user, responseDeadline);
        try {
            sendInvitationEmail(user.getEmail(), user.getUsername(), created.rawToken(), responseDeadline);
        } catch (RuntimeException mailError) {
            log.warn("초대는 재생성됐으나 메일 발송 실패: recipient={} — {}", user.getEmail(), mailError.getMessage());
        }
        return toInvitationStatus(user, created.invitation(), responseDeadline);
    }

    /**
     * @relatedFR FR-COM-01, FR-LEGAL-12, FR-LEGAL-23
     * @relatedUI UI-LEGAL-08
     * @description 사업부(BUSINESS) 계정별 초대/접근 상태 목록을 현재 활성 분기의 회신 기한 기준으로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<BusinessInvitationStatusResponse> getBusinessInvitationStatuses() {
        // 표시용 회신 기한은 초대 발송 시점 스냅샷이 아니라 현재 활성 분기의 회신 기한(라이브)을 쓴다.
        // 사업부 제출 게이트(BusinessController)와 동일 출처를 사용해 "표시 ≠ 실제 접근 기한" 불일치를 막는다.
        // 초대 행의 response_deadline 스냅샷은 초대 메일에 기재한 이력 용도로만 보존한다.
        LocalDate responseDeadline = activeResponseDeadline();
        return userRepository.findByRoleOrderByCreatedAtAsc(ROLE_BUSINESS).stream()
                .map(user -> toInvitationStatus(user, invitationService.currentInvitation(user.getId()), responseDeadline))
                .toList();
    }

    private BusinessInvitationStatusResponse toInvitationStatus(
            UserEntity user, InvitationEntity invitation, LocalDate responseDeadline) {
        return new BusinessInvitationStatusResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDepartmentId(),
                user.getDepartmentName(),
                user.getStatus(),
                invitation != null ? invitation.getStatus().name() : null,
                responseDeadline,
                invitation != null ? invitation.getInvitedAt() : null,
                invitation != null ? invitation.getExpiresAt() : null,
                invitation != null ? invitation.getAcceptedAt() : null,
                user.getLastAccessAt());
    }

    /** 현재 활성 분기의 회신 기한(submissionDeadline). 활성 분기·기한이 없으면 null. */
    private LocalDate activeResponseDeadline() {
        QuarterSettingResponse active = settingsService.getActiveQuarter();
        return active != null ? active.submissionDeadline() : null;
    }

    /**
     * @relatedFR FR-COM-01, FR-LEGAL-25
     * @relatedUI UI-LEGAL-08
     * @description 계정의 이메일·역할·사업부·이름을 수정한다(관리자 역할 제약·사업부 검증 포함).
     */
    @Transactional
    public UserResponse updateUser(String userId, CreateUserRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "사용자를 찾을 수 없습니다: " + userId));
        userRepository.findByEmail(request.email())
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                            "이미 존재하는 계정입니다: " + request.email());
                });
        validateAdminRoleChange(userId, user.getRole(), request.role());
        String departmentId = validateDepartmentId(request.role(), request.departmentId());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setDepartmentId(departmentId);
        user.setUsername(request.username());
        userRepository.save(user);
        // department 연관은 updatable=false라 departmentId를 바꿔도 갱신되지 않아 응답이 stale 사업부명을
        // 반환한다 → 검증된 departmentId로 사업부명을 직접 조회해 응답에 주입한다.
        return userResponseWithDepartmentName(user, departmentId);
    }

    /**
     * 검증된 departmentId로 사업부명을 조회해 응답을 만든다. department 연관(insertable/updatable=false)에
     * 의존하지 않으므로 createUser/updateUser 직후에도 정확한 사업부명을 반환한다. departmentId가 없으면
     * (ADMIN/LEGAL) 사업부명은 null.
     */
    private UserResponse userResponseWithDepartmentName(UserEntity user, String departmentId) {
        String departmentName = isBlank(departmentId)
                ? null
                : departmentRepository.findById(departmentId)
                        .map(department -> department.getDepartmentName())
                        .orElse(null);
        return new UserResponse(user.getId(), user.getEmail(), user.getUsername(), user.getRole(),
                user.getDepartmentId(), departmentName, user.getCreatedAt());
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-LEGAL-08
     * @description 계정을 삭제한다. 본인·기본 관리자·마지막 관리자 계정은 삭제할 수 없다.
     */
    @Transactional
    public void deleteUser(String userId, String currentUserId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "사용자를 찾을 수 없습니다: " + userId));
        if (userId.equals(currentUserId)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "자기 자신의 계정은 삭제할 수 없습니다.");
        }
        if (isProtectedAdminId(userId)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "기본 관리자 계정은 삭제할 수 없습니다.");
        }
        if (ROLE_ADMIN.equals(user.getRole()) && userRepository.countByRole(ROLE_ADMIN) <= 1) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "마지막 관리자 계정은 삭제할 수 없습니다.");
        }
        userRepository.delete(user);
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-LEGAL-08
     * @description 임시 비밀번호를 발급하고 안내 메일을 발송한다(레거시 경로, 신규 흐름은 초대 재발송 사용).
     * @deprecated 임시 비밀번호 평문 발급 방식은 초대 토큰 재발송({@link #resendInvitation})으로 대체됐다.
     *             사업부 계정 자격증명 복구는 초대 재발송이 단일 경로다. 본 메서드는 하위 호환을 위해서만 유지하며
     *             신규 흐름에서 호출하지 않는다(관리자 본인 비밀번호 변경은 별도 changePassword 경로).
     */
    @Deprecated
    @Transactional
    public ResetPasswordResponse resetPassword(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "사용자를 찾을 수 없습니다: " + userId));
        if (isProtectedAdminId(userId)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "기본 관리자 계정의 비밀번호는 초기화할 수 없습니다.");
        }
        String tempPassword = generatePassword();
        user.setPassword(passwordEncoder.encode(tempPassword));
        userRepository.save(user);
        // USER-06: 메일 발송 실패가 비밀번호 초기화를 롤백시키지 않도록 분리. 발송 여부를 응답에 반영(평문 비밀번호는 미노출).
        boolean mailSent = true;
        try {
            sendPasswordResetEmail(user.getEmail(), user.getUsername(), tempPassword);
        } catch (RuntimeException mailError) {
            mailSent = false;
            log.warn("비밀번호는 초기화됐으나 메일 발송 실패: recipient={} — {}", user.getEmail(), mailError.getMessage());
        }
        return new ResetPasswordResponse(user.getId(), user.getEmail(), "************", mailSent,
                mailSent ? "임시 비밀번호를 이메일로 발송했습니다."
                        : "비밀번호는 초기화됐으나 메일 발송에 실패했습니다. Gmail 연동을 확인해 주세요.");
    }

    private void validateAdminRoleChange(String userId, String currentRole, String requestedRole) {
        if (ROLE_ADMIN.equals(requestedRole) && hasOtherAdmin(userId)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "관리자 계정은 1개만 허용됩니다.");
        }
        if (ROLE_ADMIN.equals(currentRole) && !ROLE_ADMIN.equals(requestedRole)
                && !userRepository.existsByRoleAndIdNot(ROLE_ADMIN, userId)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "마지막 관리자 계정의 역할은 변경할 수 없습니다.");
        }
    }

    private boolean hasOtherAdmin(String userId) {
        return userId == null
                ? userRepository.existsByRole(ROLE_ADMIN)
                : userRepository.existsByRoleAndIdNot(ROLE_ADMIN, userId);
    }

    private String validateDepartmentId(String role, String departmentId) {
        String normalizedDepartmentId = normalize(departmentId);
        if (ROLE_ADMIN.equals(role) || ROLE_LEGAL.equals(role)) {
            return null;
        }
        if (ROLE_BUSINESS.equals(role) && isBlank(normalizedDepartmentId)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "사업부 계정에는 사업부 ID가 필요합니다.");
        }
        if (!isBlank(normalizedDepartmentId) && !departmentRepository.existsById(normalizedDepartmentId)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "존재하지 않는 사업부입니다: " + normalizedDepartmentId);
        }
        return normalizedDepartmentId;
    }

    private String generatePassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        return sb.toString();
    }

    private void sendInvitationEmail(String email, String username, String rawToken, LocalDate responseDeadline) {
        int ttlDays = systemSettingsService.getInvitationTtlDays();
        String acceptLink = buildAcceptLink(rawToken);
        String deadlineLine = responseDeadline != null
                ? String.format("회신 기한: %s\n", responseDeadline)
                : "";
        String subject = "[PatentFlow] 계정 초대 — 접속을 위해 비밀번호를 설정해 주세요";
        String body = String.format(
                "%s 님, PatentFlow 사업부 계정에 초대되었습니다.\n\n계정: %s\n%s"
                        + "\n아래 링크에서 비밀번호를 설정하면 계정 사용을 시작할 수 있습니다(유효기간 %d일).\n%s\n",
                username, email, deadlineLine, ttlDays, acceptLink);
        sendEmail(email, subject, body, rawToken);
    }

    /** 초대 수락 링크. 베이스 URL(FE 도메인) 설정 시 절대경로, 미설정 시 상대경로 폴백. */
    private String buildAcceptLink(String rawToken) {
        String encoded = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String base = inviteBaseUrl == null ? "" : inviteBaseUrl.trim();
        if (base.isBlank()) {
            return "/invite/accept?token=" + encoded;
        }
        String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return normalized + "/invite/accept?token=" + encoded;
    }

    private void sendPasswordResetEmail(String email, String username, String tempPassword) {
        String subject = "[PatentFlow] 비밀번호가 초기화되었습니다";
        String body = String.format(
                "%s 님, PatentFlow 계정 비밀번호가 초기화되었습니다.\n\n계정: %s\n임시 비밀번호: %s\n\n로그인 후 비밀번호를 변경해 주세요.",
                username, email, tempPassword);
        sendEmail(email, subject, body, tempPassword);
    }

    protected void sendEmail(String to, String subject, String body, String tempPassword) {
        // USER-06: 메일 레인에서 커밋 후 비동기 이벤트 발송으로 분리할 대상.
        if (!mailOAuth2Service.isConnected()) {
            log.warn("Gmail 미연동 — 이메일 발송 불가. 임시 비밀번호는 보안상 로그에 남기지 않습니다. recipient={}", to);
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "Gmail이 연동되지 않아 이메일을 발송할 수 없습니다. 설정 페이지에서 Google 계정을 먼저 연동해 주세요.");
        }
        String senderEmail = systemSettingsService.getGmailOAuth2ConnectedEmail();
        String accessToken = mailOAuth2Service.getValidAccessToken();
        sendEmailOAuth2(senderEmail, accessToken, to, subject, body);
    }

    private void sendEmailOAuth2(String senderEmail, String accessToken, String to, String subject, String body) {
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        try {
            Session session = Session.getInstance(props);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderEmail));
            message.setRecipients(MimeMessage.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject, "UTF-8");
            message.setText(body, "UTF-8");
            message.saveChanges();
            Transport transport = session.getTransport("smtp");
            transport.connect("smtp.gmail.com", 587, senderEmail, accessToken);
            transport.sendMessage(message, message.getAllRecipients());
            transport.close();
            log.info("Email sent via OAuth2 to {} (subject={})", to, subject);
        } catch (Exception e) {
            log.warn("OAuth2 email send failed to {}: {}", to, e.getMessage());
            throw new PatentFlowException(ErrorCode.MAIL_SEND_FAILED);
        }
    }

    private static boolean isProtectedAdminId(String userId) {
        return PROTECTED_ADMIN_IDS.contains(userId);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
