package com.syuuk.patentflow.patent.controller;

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

@SpringBootTest(properties = {
        "patentflow.lookup.google-patents.enabled=false"
})
@AutoConfigureMockMvc
class AnnualFeeScheduleSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiReportAgentClient aiReportAgentClient;

    @MockitoBean
    private SettingsService settingsService;

    @Test
    void annualFeeEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/annual-fees/schedule"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "BUSINESS")
    void annualFeeEndpointsRejectBusinessUsers() throws Exception {
        mockMvc.perform(get("/api/v1/annual-fees/schedule"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void annualFeeEndpointsAllowAdminUsers() throws Exception {
        mockMvc.perform(get("/api/v1/annual-fees/schedule"))
                .andExpect(status().isOk());
    }
}
