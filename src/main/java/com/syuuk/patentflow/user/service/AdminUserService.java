package com.syuuk.patentflow.user.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.dto.CreateUserRequest;
import com.syuuk.patentflow.user.dto.ResetPasswordResponse;
import com.syuuk.patentflow.user.dto.UserResponse;
import com.syuuk.patentflow.user.repository.UserRepository;
import jakarta.mail.MessagingException;
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

    public AdminUserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            SystemSettingsService systemSettingsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.systemSettingsService = systemSettingsService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers() {
        return userRepository.findAll(Sort.by("createdAt")).stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "이미 존재하는 계정입니다: " + request.username());
        }
        String tempPassword = generatePassword();
        String id = "USER-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        UserEntity user = new UserEntity(id, request.username(),
                passwordEncoder.encode(tempPassword),
                request.role(), request.departmentId(), request.displayName());
        userRepository.save(user);
        sendWelcomeEmail(user.getUsername(), user.getDisplayName(), tempPassword);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateUser(String userId, CreateUserRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "사용자를 찾을 수 없습니다: " + userId));
        userRepository.findByUsername(request.username())
                .filter(existingUser -> !existingUser.getId().equals(userId))
                .ifPresent(existingUser -> {
                    throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                            "이미 존재하는 계정입니다: " + request.username());
                });

        user.setUsername(request.username());
        user.setRole(request.role());
        user.setDepartmentId(request.departmentId());
        user.setDisplayName(request.displayName());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "사용자를 찾을 수 없습니다: " + userId));
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
        sendPasswordResetEmail(user.getUsername(), user.getDisplayName(), tempPassword);
        return new ResetPasswordResponse(user.getId(), user.getUsername(), tempPassword);
    }

    private String generatePassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private void sendWelcomeEmail(String email, String displayName, String tempPassword) {
        String subject = "[PatentFlow] 계정이 생성되었습니다";
        String body = String.format(
                "%s 님, PatentFlow 계정이 생성되었습니다.\n\n계정: %s\n임시 비밀번호: %s\n\n최초 로그인 후 비밀번호를 변경해 주세요.",
                displayName, email, tempPassword);
        sendEmail(email, subject, body, tempPassword);
    }

    private void sendPasswordResetEmail(String email, String displayName, String tempPassword) {
        String subject = "[PatentFlow] 비밀번호가 초기화되었습니다";
        String body = String.format(
                "%s 님, PatentFlow 계정 비밀번호가 초기화되었습니다.\n\n계정: %s\n임시 비밀번호: %s\n\n로그인 후 비밀번호를 변경해 주세요.",
                displayName, email, tempPassword);
        sendEmail(email, subject, body, tempPassword);
    }

    private void sendEmail(String to, String subject, String body, String tempPassword) {
        String username = resolve(systemSettingsService.getGmailUsername(), envGmailUsername);
        String password = resolve(systemSettingsService.getGmailAppPassword(), envGmailAppPassword);
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.info("Mail credentials not configured — skipping email to {}", to);
            log.info("임시 비밀번호 ({}): {}", to, tempPassword);
            return;
        }
        try {
            JavaMailSenderImpl sender = buildSender(username, password);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(username);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            sender.send(message);
            log.info("Email sent to {} (subject={})", to, subject);
        } catch (MessagingException e) {
            log.warn("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private static String resolve(String fromDb, String fromEnv) {
        return (fromDb != null && !fromDb.isBlank()) ? fromDb : fromEnv;
    }

    private static JavaMailSenderImpl buildSender(String username, String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.gmail.com");
        sender.setPort(587);
        sender.setUsername(username);
        sender.setPassword(password);
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        return sender;
    }
}
