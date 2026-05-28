package com.syuuk.patentflow.auth.controller;

import com.syuuk.patentflow.auth.dto.ChangePasswordRequest;
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

    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        authService.changePassword(authentication, request);
        // 세션 무효화 후 쿠키도 제거 — FE가 이 응답을 받으면 로그인 페이지로 이동
        authCookieService.clearAuthCookies(httpResponse);
        return ApiResponse.ok(null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            Authentication authentication,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // Authorization 헤더 우선, 없으면 쿠키에서 추출 — SPA·앱 클라이언트 모두 지원
        String accessToken = authorization != null ? authorization : authCookieService.getAccessToken(request);
        authService.logout(accessToken, authCookieService.getRefreshToken(request));
        authCookieService.clearAuthCookies(response);
        return ApiResponse.ok(null);
    }
}
