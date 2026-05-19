package com.syuuk.patentflow.auth.service;

import com.syuuk.patentflow.auth.dto.LoginRequest;
import com.syuuk.patentflow.auth.dto.LoginResponse;
import com.syuuk.patentflow.auth.dto.UpdateProfileRequest;
import com.syuuk.patentflow.auth.dto.UserPrincipalResponse;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.repository.UserRepository;
import com.syuuk.patentflow.user.security.UserDetailsImpl;
import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            UserRepository userRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtTokenProvider.createToken(userDetails);
        Instant expiresAt = jwtTokenProvider.getExpiresAt(accessToken);
        return new LoginResponse(accessToken, "Bearer", expiresAt, toPrincipalResponse(userDetails));
    }

    public UserPrincipalResponse currentUser(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return toPrincipalResponse(userDetails);
    }

    public UserPrincipalResponse updateProfile(Authentication authentication, UpdateProfileRequest request) {
        UserPrincipalResponse current = currentUser(authentication);
        UserEntity user = userRepository.findById(current.userId())
                .orElseThrow(() -> new PatentFlowException(ErrorCode.USER_NOT_FOUND));
        user.setDisplayName(request.displayName());
        userRepository.save(user);
        return currentUser(authentication);
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
}
