package com.syuuk.patentflow.user.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.mailing.service.MailOAuth2Service;
import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.dto.CreateUserRequest;
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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${spring.mail.username:}")
    private String envGmailUsername;

    @Value("${spring.mail.password:}")
    private String envGmailAppPassword;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemSettingsService systemSettingsService;
    private final MailOAuth2Service mailOAuth2Service;

    public AdminUserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            SystemSettingsService systemSettingsService, MailOAuth2Service mailOAuth2Service) {
        this.userRepository = userRepository;
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

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // 관리자는 시스템 전체에 1명만 허용 — 역할 분리 정책. 추가 관리자 필요 시 정책 변경 필요
        if ("ADMIN".equals(request.role())) {
            boolean adminExists = userRepository.findAll().stream()
                    .anyMatch(u -> "ADMIN".equals(u.getRole()));
            if (adminExists) {
                throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "관리자 계정은 1개만 허용됩니다.");
            }
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "이미 존재하는 계정입니다: " + request.email());
        }
        String tempPassword = generatePassword();
        String id = "USER-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        UserEntity user = new UserEntity(id, request.email(),
                passwordEncoder.encode(tempPassword),
                request.role(), request.departmentId(), request.username());
        userRepository.save(user);
        sendWelcomeEmail(user.getEmail(), user.getUsername(), tempPassword);
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
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setDepartmentId(request.departmentId());
        user.setUsername(request.username());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "사용자를 찾을 수 없습니다: " + userId));
        // "USER-admin"은 BootstrapAdminInitializer가 생성하는 고정 ID — 시스템 잠금 방지
        if ("USER-admin".equals(userId)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "기본 관리자 계정은 삭제할 수 없습니다.");
        }
        userRepository.delete(user);
    }

    @Transactional
    public ResetPasswordResponse resetPassword(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "사용자를 찾을 수 없습니다: " + userId));
        String tempPassword = generatePassword();
        user.setPassword(passwordEncoder.encode(tempPassword));
        userRepository.save(user);
        sendPasswordResetEmail(user.getEmail(), user.getUsername(), tempPassword);
        return new ResetPasswordResponse(user.getId(), user.getEmail(), tempPassword);
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

    private void sendEmail(String to, String subject, String body, String tempPassword) {
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
                log.warn("Gmail 미연동 — 이메일 발송 불가. 임시 비밀번호 ({}): {}", to, tempPassword);
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
}
