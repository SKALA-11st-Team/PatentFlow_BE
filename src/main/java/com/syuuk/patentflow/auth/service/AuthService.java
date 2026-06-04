package com.syuuk.patentflow.auth.service;

import com.syuuk.patentflow.auth.dto.ChangePasswordRequest;
import com.syuuk.patentflow.auth.dto.LoginRequest;
import com.syuuk.patentflow.auth.dto.LoginResponse;
import com.syuuk.patentflow.auth.dto.UpdateProfileRequest;
import com.syuuk.patentflow.auth.dto.UserPrincipalResponse;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.repository.UserRepository;
import com.syuuk.patentflow.user.security.CustomUserDetailsService;
import com.syuuk.patentflow.user.security.UserDetailsImpl;
import java.time.Instant;
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

    private final AuthenticationManager authenticationManager;
    private final AuthSessionService authSessionService;
    private final AuthTokenRevocationService tokenRevocationService;
    private final CustomUserDetailsService userDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptService loginAttemptService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AuthenticationManager authenticationManager,
            AuthSessionService authSessionService,
            AuthTokenRevocationService tokenRevocationService,
            CustomUserDetailsService userDetailsService,
            JwtTokenProvider jwtTokenProvider,
            LoginAttemptService loginAttemptService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.authenticationManager = authenticationManager;
        this.authSessionService = authSessionService;
        this.tokenRevocationService = tokenRevocationService;
        this.userDetailsService = userDetailsService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginAttemptService = loginAttemptService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
            throw exception;
        }
        loginAttemptService.recordSuccess(request.email());
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return issueTokens((UserDetailsImpl) userDetails);
    }

    @Transactional
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
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        // 비밀번호 변경 후 기존 세션 전체 무효화 — 재로그인 필요
        authSessionService.revokeAll(current.userId());
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        revokeAccessToken(accessToken);
        authSessionService.revoke(refreshToken);
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
