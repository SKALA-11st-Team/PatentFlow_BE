package com.syuuk.patentflow.auth.security;

import com.syuuk.patentflow.auth.service.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
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
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null || !jwtTokenProvider.isValid(token)) {
            return;
        }

        String username = jwtTokenProvider.getUsername(token);
        List<SimpleGrantedAuthority> authorities = jwtTokenProvider.getRoles(token).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        // DB 조회 없이 토큰 정보만으로 UserDetails 구성
        UserDetails userDetails = new User(username, "", authorities != null ? authorities : Collections.emptyList());
        
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities());
        
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
