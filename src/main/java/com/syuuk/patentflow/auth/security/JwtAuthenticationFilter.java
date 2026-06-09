package com.syuuk.patentflow.auth.security;

import com.syuuk.patentflow.auth.dto.UserPrincipalResponse;
import com.syuuk.patentflow.auth.service.AuthCookieService;
import com.syuuk.patentflow.auth.service.AuthTokenRevocationService;
import com.syuuk.patentflow.auth.service.JwtTokenProvider;
import com.syuuk.patentflow.auth.service.PasswordChangeCache;
import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthCookieService authCookieService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthTokenRevocationService tokenRevocationService;
    private final UserRepository userRepository;
    private final PasswordChangeCache passwordChangeCache;

    public JwtAuthenticationFilter(
            AuthCookieService authCookieService,
            JwtTokenProvider jwtTokenProvider,
            AuthTokenRevocationService tokenRevocationService,
            UserRepository userRepository,
            PasswordChangeCache passwordChangeCache
    ) {
        this.authCookieService = authCookieService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenRevocationService = tokenRevocationService;
        this.userRepository = userRepository;
        this.passwordChangeCache = passwordChangeCache;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            authenticate(authorization.substring(BEARER_PREFIX.length()), request);
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(authCookieService.getAccessToken(request), request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        if (token == null
                || SecurityContextHolder.getContext().getAuthentication() != null
                || tokenRevocationService.isRevoked(token)
                || !jwtTokenProvider.isValid(token)) {
            return;
        }

        UserPrincipalResponse principal = jwtTokenProvider.getUserPrincipal(token);
        if (wasIssuedBeforePasswordChange(token, principal)) {
            return;
        }
        List<SimpleGrantedAuthority> authorities = jwtTokenProvider.getRoles(token).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                authorities);

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // AUTH-08: 매 요청 DB 조회 대신 캐시(get)로 비밀번호 변경 시각을 읽는다. 미스면 DB로 폴백 후 캐시에 채운다.
    // 비밀번호 변경은 changePassword가 write-through하므로(공유 Redis면 전 레플리카 즉시 반영) 정합성이 유지된다.
    private boolean wasIssuedBeforePasswordChange(String token, UserPrincipalResponse principal) {
        Optional<Instant> cached = passwordChangeCache.get(principal.userId());
        if (cached.isPresent()) {
            return isStaleToken(token, cached.get());
        }
        Optional<UserEntity> user = userRepository.findById(principal.userId())
                .or(() -> userRepository.findByEmail(principal.email()));
        if (user.isEmpty()) {
            // 사용자 없음 → 토큰 거부. 캐시하지 않는다(사용자 생성 시 즉시 인증되도록).
            return true;
        }
        OffsetDateTime changedAt = user.get().getPasswordChangedAt();
        Instant value = changedAt == null ? PasswordChangeCache.NO_CHANGE : changedAt.toInstant();
        passwordChangeCache.put(principal.userId(), changedAt == null ? null : value);
        return isStaleToken(token, value);
    }

    private boolean isStaleToken(String token, Instant passwordChangedAt) {
        if (PasswordChangeCache.NO_CHANGE.equals(passwordChangedAt)) {
            return false;
        }
        Instant tokenPasswordChangedAt = jwtTokenProvider.getPasswordChangedAt(token);
        if (tokenPasswordChangedAt != null) {
            return !tokenPasswordChangedAt.equals(passwordChangedAt);
        }
        Instant issuedAt = jwtTokenProvider.getIssuedAt(token);
        return !issuedAt.isAfter(passwordChangedAt);
    }
}
