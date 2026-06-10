package com.syuuk.patentflow.user.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.mailing.repository.DepartmentRepository;
import com.syuuk.patentflow.mailing.service.MailOAuth2Service;
import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.dto.CreateUserRequest;
import com.syuuk.patentflow.user.dto.PageResponse;
import com.syuuk.patentflow.user.dto.ResetPasswordResponse;
import com.syuuk.patentflow.user.dto.UserResponse;
import com.syuuk.patentflow.user.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.security.SecureRandom;
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
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_BUSINESS = "BUSINESS";
    private static final Set<String> PROTECTED_ADMIN_IDS = Set.of("USER-admin", "USER-admin-bootstrap");

    @Value("${spring.mail.username:}")
    private String envGmailUsername;

    @Value("${spring.mail.password:}")
    private String envGmailAppPassword;

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemSettingsService systemSettingsService;
    private final MailOAuth2Service mailOAuth2Service;

    public AdminUserService(UserRepository userRepository, DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder,
            SystemSettingsService systemSettingsService, MailOAuth2Service mailOAuth2Service) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.systemSettingsService = systemSettingsService;
        this.mailOAuth2Service = mailOAuth2Service;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers() {
        return userRepository.findAll(Sort.by("createdAt")).stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(int page, int size, String search) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("createdAt"));
        Page<UserEntity> users = isBlank(search)
                ? userRepository.findAll(pageable)
                : userRepository.findByEmailContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrDepartmentIdContainingIgnoreCase(
                        search.trim(), search.trim(), search.trim(), pageable);
        return PageResponse.from(users.map(UserResponse::from));
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        validateAdminRoleChange(null, null, request.role());
        String departmentId = validateDepartmentId(request.role(), request.departmentId());
        if (userRepository.existsByEmail(request.email())) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "이미 존재하는 계정입니다: " + request.email());
        }
        String tempPassword = generatePassword();
        String id = "USER-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        UserEntity user = new UserEntity(id, request.email(),
                passwordEncoder.encode(tempPassword),
                request.role(), departmentId, request.username());
        userRepository.save(user);
        // USER-06: 메일 발송 실패가 계정 생성을 롤백시키지 않도록 분리한다(커밋된 계정은 보존, 발송 실패는 로깅).
        try {
            sendWelcomeEmail(user.getEmail(), user.getUsername(), tempPassword);
        } catch (RuntimeException mailError) {
            log.warn("계정은 생성됐으나 환영 메일 발송 실패: recipient={} — {}", user.getEmail(), mailError.getMessage());
        }
        return UserResponse.from(user);
    }

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
        return UserResponse.from(userRepository.save(user));
    }

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
        if (ROLE_ADMIN.equals(role)) {
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

    private void sendWelcomeEmail(String email, String username, String tempPassword) {
        String subject = "[PatentFlow] 계정이 생성되었습니다";
        String body = String.format(
                "%s 님, PatentFlow 계정이 생성되었습니다.\n\n계정: %s\n임시 비밀번호: %s\n\n최초 로그인 후 비밀번호를 변경해 주세요.",
                username, email, tempPassword);
        sendEmail(email, subject, body, tempPassword);
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
        if (mailOAuth2Service.isConnected()) {
            // OAuth2 연동 계정으로 발송 (권장)
            String senderEmail = systemSettingsService.getGmailOAuth2ConnectedEmail();
            String accessToken = mailOAuth2Service.getValidAccessToken();
            sendEmailOAuth2(senderEmail, accessToken, to, subject, body);
        } else {
            // 레거시 앱 비밀번호 폴백
            String senderEmail = resolve(systemSettingsService.getGmailUsername(), envGmailUsername);
            String password = resolve(systemSettingsService.getGmailAppPassword(), envGmailAppPassword);
            if (senderEmail == null || senderEmail.isBlank() || password == null || password.isBlank()) {
                log.warn("Gmail 미연동 — 이메일 발송 불가. 임시 비밀번호는 보안상 로그에 남기지 않습니다. recipient={}", to);
                throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "Gmail이 연동되지 않아 이메일을 발송할 수 없습니다. 설정 페이지에서 Google 계정을 먼저 연동해 주세요.");
            }
            sendEmailSmtp(senderEmail, password, to, subject, body);
        }
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

    private void sendEmailSmtp(String senderEmail, String password, String to, String subject, String body) {
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost("smtp.gmail.com");
            sender.setPort(587);
            sender.setUsername(senderEmail);
            sender.setPassword(password);
            Properties props = sender.getJavaMailProperties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(senderEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            sender.send(message);
            log.info("Email sent via SMTP to {} (subject={})", to, subject);
        } catch (MessagingException e) {
            log.warn("SMTP email send failed to {}: {}", to, e.getMessage());
            throw new PatentFlowException(ErrorCode.MAIL_SEND_FAILED);
        }
    }

    private static String resolve(String fromDb, String fromEnv) {
        return (fromDb != null && !fromDb.isBlank()) ? fromDb : fromEnv;
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
