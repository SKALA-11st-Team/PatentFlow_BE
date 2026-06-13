package com.syuuk.patentflow.auth.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.syuuk.patentflow.patent.client.AiReportAgentClient;
import com.syuuk.patentflow.settings.service.SettingsService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * BE-14: CSRF/권한 거부가 ERROR dispatch 없이 식별 가능한 403 JSON으로 응답되는지 검증한다.
 * CSRF는 기본 활성(테스트 yml에 별도 비활성 설정 없음)이다.
 */
@SpringBootTest(properties = {
        "patentflow.lookup.google-patents.enabled=false",
        // 컨텍스트 캐시 키 분리용: 공유 H2(mem:patentflow-test)에서 동일 키 컨텍스트를
        // 재사용하면 중간에 부팅한 다른 컨텍스트가 DB를 재생성해 stale 데이터를 읽게 된다.
        "patentflow.test.context-id=csrf-denial"
})
@AutoConfigureMockMvc
class CsrfDenialResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiReportAgentClient aiReportAgentClient;

    @MockitoBean
    private SettingsService settingsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void csrfMissingPostReturnsIdentifiable403Json() throws Exception {
        mockMvc.perform(post("/api/v1/mailings/send")
                        .contentType("application/json")
                        .content("{\"drafts\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void csrfValidPostPassesCsrfFilter() throws Exception {
        // .with(csrf())는 공유 CsrfFilter의 저장소를 Lazy 저장소로 영구 치환해 다른 테스트의
        // 쿠키 발급을 막는다 — 실제 프라이밍 GET으로 받은 쿠키/헤더 왕복으로 검증한다(FE와 동일 경로).
        Cookie xsrfCookie = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        if (xsrfCookie == null) {
            throw new AssertionError("프라이밍 응답에 XSRF-TOKEN 쿠키가 없음");
        }
        mockMvc.perform(post("/api/v1/mailings/send")
                        .cookie(new Cookie("XSRF-TOKEN", xsrfCookie.getValue()))
                        .header("X-XSRF-TOKEN", xsrfCookie.getValue())
                        .contentType("application/json")
                        .content("{\"drafts\":[]}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403) {
                        throw new AssertionError("CSRF 토큰이 유효한데 403이 반환됨");
                    }
                });
    }

    @Test
    void csrfPrimingEndpointIssuesTokenCookieWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    boolean hasXsrfCookie = result.getResponse().getCookie("XSRF-TOKEN") != null
                            || result.getResponse().getHeaders("Set-Cookie").stream()
                                    .anyMatch(value -> value.startsWith("XSRF-TOKEN="));
                    if (!hasXsrfCookie) {
                        throw new AssertionError("XSRF-TOKEN Set-Cookie가 응답에 없음");
                    }
                });
    }

    @Test
    @WithMockUser(roles = "BUSINESS")
    void roleDenialReturnsAccessDeniedJson() throws Exception {
        mockMvc.perform(get("/api/v1/annual-fees/schedule"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
