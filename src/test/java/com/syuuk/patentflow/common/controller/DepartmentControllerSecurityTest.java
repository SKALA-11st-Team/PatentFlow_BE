package com.syuuk.patentflow.common.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.syuuk.patentflow.patent.client.AiReportAgentClient;
import com.syuuk.patentflow.settings.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FE-01: 부서 목록 GET은 ADMIN+LEGAL 읽기 허용, 그 외 역할은 차단을 검증한다.
 */
@SpringBootTest(properties = {
        "patentflow.lookup.google-patents.enabled=false",
        // 컨텍스트 캐시 키 분리용: AnnualFeeScheduleSecurityTest와 키가 겹치면 공유 H2에서
        // stale 컨텍스트 재사용 문제가 생긴다(CsrfDenialResponseTest 주석 참고).
        "patentflow.test.context-id=department-security"
})
@AutoConfigureMockMvc
class DepartmentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiReportAgentClient aiReportAgentClient;

    @MockitoBean
    private SettingsService settingsService;

    @Test
    void departmentsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/departments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "BUSINESS")
    void departmentsRejectBusinessUsers() throws Exception {
        mockMvc.perform(get("/api/v1/departments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "LEGAL")
    void departmentsAllowLegalUsers() throws Exception {
        mockMvc.perform(get("/api/v1/departments"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void departmentsAllowAdminUsers() throws Exception {
        mockMvc.perform(get("/api/v1/departments"))
                .andExpect(status().isOk());
    }
}
