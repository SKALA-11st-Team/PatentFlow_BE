package com.syuuk.patentflow.auth.service;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AuthSessionService authSessionService;
    private final AuthTokenRevocationService tokenRevocationService;
    private final CustomUserDetailsService userDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptService loginAttemptService;
    private final UserRepository userRepository;

    public AuthService(
            AuthenticationManager authenticationManager,
            AuthSessionService authSessionService,
            AuthTokenRevocationService tokenRevocationService,
            CustomUserDetailsService userDetailsService,
            JwtTokenProvider jwtTokenProvider,
            LoginAttemptService loginAttemptService,
            UserRepository userRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.authSessionService = authSessionService;
        this.tokenRevocationService = tokenRevocationService;
        this.userDetailsService = userDetailsService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginAttemptService = loginAttemptService;
        this.userRepository = userRepository;
    }

    public AuthResult login(LoginRequest request) {
        loginAttemptService.assertNotLocked(request.username());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (BadCredentialsException exception) {
            loginAttemptService.recordFailure(request.username());
            throw exception;
        }
        loginAttemptService.recordSuccess(request.username());
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return issueTokens((UserDetailsImpl) userDetails);
    }

    public AuthResult refresh(String refreshToken) {
        AuthSessionService.UserSession session = authSessionService.consume(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(session.username());
        return issueTokens((UserDetailsImpl) userDetails);
    }

    public UserPrincipalResponse currentUser(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipalResponse userPrincipal) {
            return userPrincipal;
        }
        return toPrincipalResponse((UserDetails) principal);
    }

    public UserPrincipalResponse updateProfile(Authentication authentication, UpdateProfileRequest request) {
        UserPrincipalResponse current = currentUser(authentication);
        UserEntity user = userRepository.findById(current.userId())
                .orElseThrow(() -> new PatentFlowException(ErrorCode.USER_NOT_FOUND));
        user.setDisplayName(request.displayName());
        userRepository.save(user);
        return toPrincipalResponse(new UserDetailsImpl(user));
    }

    public void logout(String accessToken, String refreshToken) {
        revokeAccessToken(accessToken);
        authSessionService.revoke(refreshToken);
    }

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
            String role = u.getRole();
            return new UserPrincipalResponse(
                    u.getUsername(), u.getDisplayName(), roles,
                    u.getId(), u.getDisplayName(), u.getUsername(),
                    role, u.getDepartmentId(), u.getDepartmentName());
        }

        return new UserPrincipalResponse(userDetails.getUsername(), userDetails.getUsername(), roles);
    }

    public record AuthResult(
            LoginResponse response,
            String accessToken,
            Instant accessExpiresAt,
            String refreshToken,
            Instant refreshExpiresAt
    ) {}
}
