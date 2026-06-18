/**
 * @author 유건욱
 * @date 2026-05-19
 */
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

/**
 * @relatedFR FR-COM-01
 * @relatedUI UI-COM-01
 * @description 로그인·토큰 갱신·로그아웃과 본인 프로필/비밀번호 관리를 처리하는 인증 API.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    public AuthController(AuthService authService, AuthCookieService authCookieService) {
        this.authService = authService;
        this.authCookieService = authCookieService;
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-COM-01
     * @description 이메일/비밀번호로 로그인하고 access·refresh 토큰을 HttpOnly 쿠키로 발급한다.
     */
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

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-COM-01
     * @description refresh 쿠키로 세션을 검증해 새 access·refresh 토큰을 재발급한다.
     */
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

    /**
     * BE-14: SPA가 XSRF-TOKEN 쿠키를 강제 재발급받기 위한 no-op GET.
     * CsrfCookieFilter가 deferred 토큰을 렌더링해 응답에 Set-Cookie를 싣는다.
     */
    @GetMapping("/csrf")
    public ApiResponse<Void> csrf() {
        return ApiResponse.ok(null);
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-COM-01
     * @description 현재 로그인한 사용자의 프로필(이메일·이름·역할·부서)을 조회한다.
     */
    @GetMapping("/me")
    public ApiResponse<UserPrincipalResponse> me(Authentication authentication) {
        return ApiResponse.ok(authService.currentUser(authentication));
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-COM-01
     * @description 로그인한 사용자가 본인 프로필(표시 이름)을 수정한다.
     */
    @PatchMapping("/me")
    public ApiResponse<UserPrincipalResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ApiResponse.ok(authService.updateProfile(authentication, request));
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-COM-01
     * @description 본인 비밀번호를 변경하고 기존 세션·인증 쿠키를 무효화해 재로그인을 강제한다.
     */
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

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-COM-01
     * @description access·refresh 토큰을 폐기하고 인증 쿠키를 제거해 로그아웃한다.
     */
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
