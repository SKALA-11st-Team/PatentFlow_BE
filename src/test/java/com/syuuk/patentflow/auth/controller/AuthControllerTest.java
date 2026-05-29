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
        "patentflow.lookup.google-patents.enabled=false",
        "patentflow.auth.max-login-failures=2",
        "patentflow.auth.login-lock-seconds=300",
        "patentflow.bootstrap.admin.username=admin",
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
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.displayName").value("특허관리자"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_ADMIN"));
    }

    @Test
    void loginSetsHttpOnlyCookiesAndCookieAuthenticatesCurrentUser() throws Exception {
        LoginCapture login = loginCapture("admin", "admin1234");

        mockMvc.perform(get("/api/v1/auth/me")
                .cookie(login.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    void refreshRotatesSessionCookieAndIssuesNewAccessCookie() throws Exception {
        LoginCapture login = loginCapture("admin", "admin1234");

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

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(login.refreshCookie()))
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
    void loginRejectsInvalidPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "admin",
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
    void businessUserCannotAccessAdminApi() throws Exception {
        ensureBusinessUser();
        String token = login("business-user", "business1234");

        mockMvc.perform(get("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessUserCannotReadOrMutateAdminNotifications() throws Exception {
        ensureBusinessUser();
        String token = login("business-user", "business1234");

        mockMvc.perform(get("/api/v1/notifications")
                .param("role", "ADMIN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/notifications/NOTIF-001/read-state")
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
                              "username": "lock-user",
                              "password": "wrong-password"
                            }
                            """))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "lock-user",
                          "password": "lock1234"
                        }
                        """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
    }

    @Test
    void businessUserCanAccessOnlyAssignedDepartmentPatentResources() throws Exception {
        ensureBusinessUser();
        String token = login("business-user", "business1234");

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
        String token = login("stale-dept-user", "business1234");

        UserEntity user = userRepository.findByUsername("stale-dept-user").orElseThrow();
        user.setDepartmentId("DEPT-ICT");
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/business/patents/PAT-2026-0127")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patentId").value("PAT-2026-0127"))
                .andExpect(jsonPath("$.data.departmentId").value("DEPT-ICT"));
    }

    private String loginAsAdmin() throws Exception {
        return login("admin", "admin1234");
    }

    private String login(String username, String password) throws Exception {
        return loginCapture(username, password).accessToken();
    }

    private LoginCapture loginCapture(String username, String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "%s",
                          "password": "%s"
                        }
                        """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andReturn()
                .getResponse();

        String accessToken = JsonPath.read(result.getContentAsString(), "$.data.accessToken");
        return new LoginCapture(
                accessToken,
                result.getCookie("patentflow_access"),
                result.getCookie("patentflow_refresh"));
    }

    private void ensureBusinessUser() {
        if (userRepository.existsByUsername("business-user")) {
            return;
        }
        userRepository.save(new UserEntity(
                "USER-business-test",
                "business-user",
                passwordEncoder.encode("business1234"),
                "BUSINESS",
                "DEPT-RND",
                "사업부 테스트 사용자"));
    }

    private void ensureStaleDepartmentUser() {
        userRepository.findByUsername("stale-dept-user").ifPresentOrElse(
                user -> {
                    user.setDepartmentId("DEPT-RND");
                    userRepository.save(user);
                },
                () -> userRepository.save(new UserEntity(
                        "USER-stale-dept-test",
                        "stale-dept-user",
                        passwordEncoder.encode("business1234"),
                        "BUSINESS",
                        "DEPT-RND",
                        "부서 변경 테스트 사용자")));
    }

    private void ensureLockTestUser() {
        if (userRepository.existsByUsername("lock-user")) {
            return;
        }
        userRepository.save(new UserEntity(
                "USER-lock-test",
                "lock-user",
                passwordEncoder.encode("lock1234"),
                "ADMIN",
                null,
                "잠금 테스트 사용자"));
    }

    private record LoginCapture(String accessToken, Cookie accessCookie, Cookie refreshCookie) {}
}
