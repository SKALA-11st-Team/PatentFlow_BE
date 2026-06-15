package com.syuuk.patentflow.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "patentflow.lookup.kipris.enabled=false",
        "patentflow.lookup.google-patents.enabled=false",
        "patentflow.auth.max-login-failures=2",
        "patentflow.auth.login-lock-seconds=300",
        "management.endpoint.health.probes.enabled=true",
        // bootstrap 환경변수명이 .email로 변경됨 (users.email = 로그인 ID)
        "patentflow.bootstrap.admin.email=admin@test.com",
        "patentflow.bootstrap.admin.password=admin1234"
})
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Test
    void loginReturnsJwtTokenAndCurrentUser() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                // email = 로그인 ID, username = 실제 이름
                .andExpect(jsonPath("$.data.email").value("admin@test.com"))
                .andExpect(jsonPath("$.data.username").value("특허관리자"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_ADMIN"));
    }

    @Test
    void loginSetsHttpOnlyCookiesAndCookieAuthenticatesCurrentUser() throws Exception {
        LoginCapture login = loginCapture("admin@test.com", "admin1234");

        mockMvc.perform(get("/api/v1/auth/me")
                .cookie(login.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("admin@test.com"));
    }

    @Test
    void refreshRotatesSessionCookieAndIssuesNewAccessCookie() throws Exception {
        LoginCapture login = loginCapture("admin@test.com", "admin1234");

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(login.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(result -> {
                    Cookie accessCookie = result.getResponse().getCookie("patentflow_access");
                    Cookie refreshCookie = result.getResponse().getCookie("patentflow_refresh");
                    org.assertj.core.api.Assertions.assertThat(accessCookie).isNotNull();
                    org.assertj.core.api.Assertions.assertThat(refreshCookie).isNotNull();
                    org.assertj.core.api.Assertions.assertThat(accessCookie.isHttpOnly()).isTrue();
                    org.assertj.core.api.Assertions.assertThat(refreshCookie.isHttpOnly()).isTrue();
                });

        // refresh_token은 1회성 — 재사용 시 UNAUTHORIZED
        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(login.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenReuseRevokesOtherSessionsForSameUser() throws Exception {
        ensureRefreshReuseTestUser();
        LoginCapture firstLogin = loginCapture("refresh-reuse@test.com", "OldPass123!");
        LoginCapture secondLogin = loginCapture("refresh-reuse@test.com", "OldPass123!");

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(firstLogin.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(firstLogin.refreshCookie()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(secondLogin.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedApiRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/patents"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/patents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void operationalEndpointsExposeOnlyHealthPublicly() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerEndpointsRequireAdminAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v3/api-docs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAsAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void loginRejectsInvalidPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "admin@test.com",
                          "password": "wrong-password"
                        }
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void logoutRevokesCurrentAccessToken() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(post("/api/v1/auth/logout")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRequiresCsrfTokenToPreventForcedLogout() throws Exception {
        // be-auth-3: logout은 CSRF 면제에서 제거됨 — 토큰 없는 cross-site POST는 강제 로그아웃을 일으키지 못한다.
        String token = loginAsAdmin();

        mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        // CSRF 토큰 없이 거부됐으므로 세션은 여전히 유효해야 한다.
        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void businessUserCannotAccessAdminApi() throws Exception {
        ensureBusinessUser();
        String token = login("business@test.com", "business1234");

        mockMvc.perform(get("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessUserCannotReadOrMutateAdminNotifications() throws Exception {
        ensureBusinessUser();
        String token = login("business@test.com", "business1234");

        mockMvc.perform(get("/api/v1/notifications")
                .param("role", "ADMIN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/notifications/NOTIF-001/read-state")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "isRead": true
                        }
                        """)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanReadChecklistItemsForBusinessSubmissionDisplay() throws Exception {
        mockMvc.perform(get("/api/v1/business/checklist-items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void repeatedLoginFailuresLockAccountTemporarily() throws Exception {
        ensureLockTestUser();

        for (int i = 0; i < 2; i += 1) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "email": "lock@test.com",
                              "password": "wrong-password"
                            }
                            """))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "lock@test.com",
                          "password": "lock1234"
                        }
                        """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
    }

    @Test
    void changePasswordInvalidatesExistingAccessTokenAndRequiresStrongPassword() throws Exception {
        ensurePasswordChangeTestUser();
        String token = login("password-change@test.com", "OldPass123!");

        mockMvc.perform(patch("/api/v1/auth/password")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "currentPassword": "OldPass123!",
                          "newPassword": "weakpass123"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(patch("/api/v1/auth/password")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "currentPassword": "OldPass123!",
                          "newPassword": "BetterPass123!"
                        }
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "password-change@test.com",
                          "password": "OldPass123!"
                        }
                        """))
                .andExpect(status().isUnauthorized());

        login("password-change@test.com", "BetterPass123!");
    }

    @Test
    void businessUserCanAccessOnlyAssignedDepartmentPatentResources() throws Exception {
        ensureBusinessUser();
        String token = login("business@test.com", "business1234");

        mockMvc.perform(get("/api/v1/business/patents/PAT-2026-0001")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.departmentId").value("DEPT-RND"));

        mockMvc.perform(get("/api/v1/patents/PAT-2026-0001")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/business/patents/PAT-2026-0002")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void businessPatentAccessUsesCurrentUserDepartmentWhenJwtDepartmentIsStale() throws Exception {
        ensureStaleDepartmentUser();
        String token = login("stale@test.com", "business1234");

        // JWT 발급 후 DB에서 사업부를 변경 → 다음 요청 시 DB 최신값 반영 여부 확인
        UserEntity user = userRepository.findByEmail("stale@test.com").orElseThrow();
        user.setDepartmentId("DEPT-ICT");
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/business/patents/PAT-2026-0127")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patentId").value("PAT-2026-0127"))
                .andExpect(jsonPath("$.data.departmentId").value("DEPT-ICT"));
    }

    private String loginAsAdmin() throws Exception {
        return login("admin@test.com", "admin1234");
    }

    private String login(String username, String password) throws Exception {
        return loginCapture(username, password).accessToken();
    }

    private LoginCapture loginCapture(String username, String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                // 로그인 응답의 user.email = 로그인 ID
                .andExpect(jsonPath("$.data.user.email").value(username))
                .andReturn()
                .getResponse();

        String accessToken = JsonPath.read(result.getContentAsString(), "$.data.accessToken");
        return new LoginCapture(
                accessToken,
                result.getCookie("patentflow_access"),
                result.getCookie("patentflow_refresh"));
    }

    private void ensureBusinessUser() {
        if (userRepository.existsByEmail("business@test.com")) {
            return;
        }
        // UserEntity(id, email, password, role, departmentId, username=이름)
        userRepository.save(new UserEntity(
                "USER-business-test",
                "business@test.com",
                passwordEncoder.encode("business1234"),
                "BUSINESS",
                "DEPT-RND",
                "사업부 테스트 사용자"));
    }

    private void ensureStaleDepartmentUser() {
        userRepository.findByEmail("stale@test.com").ifPresentOrElse(
                user -> {
                    user.setDepartmentId("DEPT-RND");
                    userRepository.save(user);
                },
                () -> userRepository.save(new UserEntity(
                        "USER-stale-dept-test",
                        "stale@test.com",
                        passwordEncoder.encode("business1234"),
                        "BUSINESS",
                        "DEPT-RND",
                        "부서 변경 테스트 사용자")));
    }

    private void ensureLockTestUser() {
        if (userRepository.existsByEmail("lock@test.com")) {
            return;
        }
        userRepository.save(new UserEntity(
                "USER-lock-test",
                "lock@test.com",
                passwordEncoder.encode("lock1234"),
                "ADMIN",
                null,
                "잠금 테스트 사용자"));
    }

    private void ensurePasswordChangeTestUser() {
        userRepository.findByEmail("password-change@test.com").ifPresentOrElse(
                user -> {
                    user.setPassword(passwordEncoder.encode("OldPass123!"));
                    user.setPasswordChangedAt(null);
                    userRepository.save(user);
                },
                () -> userRepository.save(new UserEntity(
                        "USER-password-change-test",
                        "password-change@test.com",
                        passwordEncoder.encode("OldPass123!"),
                        "ADMIN",
                        null,
                        "비밀번호 변경 테스트 사용자")));
    }

    private void ensureRefreshReuseTestUser() {
        userRepository.findByEmail("refresh-reuse@test.com").ifPresentOrElse(
                user -> {
                    user.setPassword(passwordEncoder.encode("OldPass123!"));
                    user.setPasswordChangedAt(null);
                    userRepository.save(user);
                },
                () -> userRepository.save(new UserEntity(
                        "USER-refresh-reuse-test",
                        "refresh-reuse@test.com",
                        passwordEncoder.encode("OldPass123!"),
                        "ADMIN",
                        null,
                        "리프레시 재사용 테스트 사용자")));
    }

    private record LoginCapture(String accessToken, Cookie accessCookie, Cookie refreshCookie) {}
}
