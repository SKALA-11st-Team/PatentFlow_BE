package com.syuuk.patentflow.auth.controller;

import com.syuuk.patentflow.auth.dto.LoginRequest;
import com.syuuk.patentflow.auth.dto.LoginResponse;
import com.syuuk.patentflow.auth.dto.UpdateProfileRequest;
import com.syuuk.patentflow.auth.dto.UserPrincipalResponse;
import com.syuuk.patentflow.auth.service.AuthCookieService;
import com.syuuk.patentflow.auth.service.AuthService;
import com.syuuk.patentflow.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    public AuthController(AuthService authService, AuthCookieService authCookieService) {
        this.authService = authService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthService.AuthResult result = authService.login(request);
        authCookieService.writeAuthCookies(
                response,
                result.accessToken(),
                result.accessExpiresAt(),
                result.refreshToken(),
                result.refreshExpiresAt());
        return ApiResponse.ok(result.response());
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        AuthService.AuthResult result = authService.refresh(authCookieService.getRefreshToken(request));
        authCookieService.writeAuthCookies(
                response,
                result.accessToken(),
                result.accessExpiresAt(),
                result.refreshToken(),
                result.refreshExpiresAt());
        return ApiResponse.ok(result.response());
    }

    @GetMapping("/me")
    public ApiResponse<UserPrincipalResponse> me(Authentication authentication) {
        return ApiResponse.ok(authService.currentUser(authentication));
    }

    @PatchMapping("/me")
    public ApiResponse<UserPrincipalResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ApiResponse.ok(authService.updateProfile(authentication, request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            Authentication authentication,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String accessToken = authorization != null ? authorization : authCookieService.getAccessToken(request);
        authService.logout(accessToken, authCookieService.getRefreshToken(request));
        authCookieService.clearAuthCookies(response);
        return ApiResponse.ok(null);
    }
}
