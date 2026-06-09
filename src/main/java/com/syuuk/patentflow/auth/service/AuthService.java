package com.syuuk.patentflow.auth.service;

import com.syuuk.patentflow.auth.dto.ChangePasswordRequest;
import com.syuuk.patentflow.auth.dto.LoginRequest;
import com.syuuk.patentflow.auth.dto.LoginResponse;
import com.syuuk.patentflow.auth.dto.UpdateProfileRequest;
import com.syuuk.patentflow.auth.dto.UserPrincipalResponse;
import com.syuuk.patentflow.common.audit.SecurityAuditLogger;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.repository.UserRepository;
import com.syuuk.patentflow.user.security.CustomUserDetailsService;
import com.syuuk.patentflow.user.security.UserDetailsImpl;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AuthenticationManager authenticationManager;
    private final AuthSessionService authSessionService;
    private final AuthTokenRevocationService tokenRevocationService;
    private final CustomUserDetailsService userDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptService loginAttemptService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditLogger auditLogger;

    public AuthService(
            AuthenticationManager authenticationManager,
            AuthSessionService authSessionService,
            AuthTokenRevocationService tokenRevocationService,
            CustomUserDetailsService userDetailsService,
            JwtTokenProvider jwtTokenProvider,
            LoginAttemptService loginAttemptService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SecurityAuditLogger auditLogger
    ) {
        this.authenticationManager = authenticationManager;
        this.authSessionService = authSessionService;
        this.tokenRevocationService = tokenRevocationService;
        this.userDetailsService = userDetailsService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginAttemptService = loginAttemptService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        loginAttemptService.assertNotLocked(request.email());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException exception) {
            loginAttemptService.recordFailure(request.email());
            auditLogger.record(SecurityAuditLogger.Event.LOGIN_FAILURE, request.email());
            throw exception;
        }
        loginAttemptService.recordSuccess(request.email());
        auditLogger.record(SecurityAuditLogger.Event.LOGIN_SUCCESS, request.email());
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return issueTokens((UserDetailsImpl) userDetails);
    }

    @Transactional(noRollbackFor = PatentFlowException.class)
    public AuthResult refresh(String refreshToken) {
        AuthSessionService.UserSession session = authSessionService.consume(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(session.email());
        return issueTokens((UserDetailsImpl) userDetails);
    }

    @Transactional(readOnly = true)
    public UserPrincipalResponse currentUser(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipalResponse userPrincipal) {
            // JWT 파싱된 principal로 DB를 재조회해 최신 프로필(username, 부서 등)을 반환
            return userRepository.findById(userPrincipal.userId())
                    .or(() -> userRepository.findByEmail(userPrincipal.email()))
                    .map(user -> toPrincipalResponse(new UserDetailsImpl(user)))
                    .orElse(userPrincipal);
        }
        return toPrincipalResponse((UserDetails) principal);
    }

    @Transactional
    public UserPrincipalResponse updateProfile(Authentication authentication, UpdateProfileRequest request) {
        UserPrincipalResponse current = currentUser(authentication);
        UserEntity user = userRepository.findById(current.userId())
                .orElseThrow(() -> new PatentFlowException(ErrorCode.USER_NOT_FOUND));
        user.setUsername(request.username());
        userRepository.save(user);
        return toPrincipalResponse(new UserDetailsImpl(user));
    }

    @Transactional
    public void changePassword(Authentication authentication, ChangePasswordRequest request) {
        UserPrincipalResponse current = currentUser(authentication);
        UserEntity user = userRepository.findById(current.userId())
                .orElseThrow(() -> new PatentFlowException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "현재 비밀번호가 올바르지 않습니다.");
        }
        validateNewPassword(request.newPassword(), request.currentPassword(), user);
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(OffsetDateTime.now(KST).withNano(0));
        userRepository.save(user);
        // 비밀번호 변경 후 기존 세션 전체 무효화 — 재로그인 필요
        authSessionService.revokeAll(current.userId());
        auditLogger.record(SecurityAuditLogger.Event.PASSWORD_CHANGED, current.email());
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        revokeAccessToken(accessToken);
        authSessionService.revoke(refreshToken);
        auditLogger.record(SecurityAuditLogger.Event.LOGOUT, null);
    }

    @Transactional
    public void revokeAccessToken(String accessToken) {
        String token = extractBearerToken(accessToken);
        if (token == null) {
            token = accessToken;
        }
        if (token == null || !jwtTokenProvider.isValid(token)) {
            return;
        }
        tokenRevocationService.revoke(token, jwtTokenProvider.getExpiresAt(token));
    }

    private String extractBearerToken(String value) {
        if (value == null || !value.startsWith("Bearer ")) {
            return null;
        }
        return value.substring("Bearer ".length());
    }

    private AuthResult issueTokens(UserDetailsImpl userDetails) {
        String accessToken = jwtTokenProvider.createToken(userDetails);
        Instant accessExpiresAt = jwtTokenProvider.getExpiresAt(accessToken);
        AuthSessionService.RefreshSession refreshSession = authSessionService.create(userDetails);
        LoginResponse response = new LoginResponse(
                accessToken,
                "Bearer",
                accessExpiresAt,
                toPrincipalResponse(userDetails),
                null);
        return new AuthResult(response, accessToken, accessExpiresAt, refreshSession.refreshToken(), refreshSession.expiresAt());
    }

    private void validateNewPassword(String newPassword, String currentPassword, UserEntity user) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "새 비밀번호를 입력해주세요.");
        }
        if (newPassword.length() < 10 || newPassword.length() > 128) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "비밀번호는 10자 이상 128자 이하로 입력해주세요.");
        }
        if (newPassword.equals(currentPassword) || passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "기존 비밀번호와 다른 비밀번호를 사용해주세요.");
        }
        if (!hasUppercase(newPassword) || !hasLowercase(newPassword)
                || !hasDigit(newPassword) || !hasSpecialCharacter(newPassword)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "비밀번호는 대문자, 소문자, 숫자, 특수문자를 모두 포함해야 합니다.");
        }
        String emailPrefix = user.getEmail() == null ? "" : user.getEmail().split("@", 2)[0].toLowerCase();
        String username = user.getUsername() == null ? "" : user.getUsername().toLowerCase();
        String normalizedPassword = newPassword.toLowerCase();
        if (!emailPrefix.isBlank() && normalizedPassword.contains(emailPrefix)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "비밀번호에 이메일 ID를 포함할 수 없습니다.");
        }
        if (!username.isBlank() && normalizedPassword.contains(username)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "비밀번호에 사용자 이름을 포함할 수 없습니다.");
        }
    }

    private boolean hasUppercase(String value) {
        return value.chars().anyMatch(Character::isUpperCase);
    }

    private boolean hasLowercase(String value) {
        return value.chars().anyMatch(Character::isLowerCase);
    }

    private boolean hasDigit(String value) {
        return value.chars().anyMatch(Character::isDigit);
    }

    private boolean hasSpecialCharacter(String value) {
        return value.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
    }

    private UserPrincipalResponse toPrincipalResponse(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        if (userDetails instanceof UserDetailsImpl impl) {
            UserEntity u = impl.getUser();
            return new UserPrincipalResponse(
                    u.getEmail(), u.getUsername(), roles,
                    u.getId(), u.getRole(),
                    u.getDepartmentId(), u.getDepartmentName());
        }
        // fallback — email = Spring Security username (login ID)
        String email = userDetails.getUsername();
        return new UserPrincipalResponse(email, email, roles, email, "BUSINESS", null, null);
    }

    public record AuthResult(
            LoginResponse response,
            String accessToken,
            Instant accessExpiresAt,
            String refreshToken,
            Instant refreshExpiresAt
    ) {}
}
