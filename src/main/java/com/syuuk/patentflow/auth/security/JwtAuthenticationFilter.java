package com.syuuk.patentflow.auth.security;

import com.syuuk.patentflow.auth.dto.UserPrincipalResponse;
import com.syuuk.patentflow.auth.service.AuthCookieService;
import com.syuuk.patentflow.auth.service.AuthTokenRevocationService;
import com.syuuk.patentflow.auth.service.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
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

    public JwtAuthenticationFilter(
            AuthCookieService authCookieService,
            JwtTokenProvider jwtTokenProvider,
            AuthTokenRevocationService tokenRevocationService
    ) {
        this.authCookieService = authCookieService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenRevocationService = tokenRevocationService;
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
        } else {
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
}
