package com.syuuk.patentflow.patent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.syuuk.patentflow.auth.dto.UserPrincipalResponse;
import com.syuuk.patentflow.patent.client.AiReportAgentClient;
import com.syuuk.patentflow.patent.domain.AnnualFeeAdjustmentEntity;
import com.syuuk.patentflow.patent.repository.AnnualFeeAdjustmentRepository;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import com.syuuk.patentflow.settings.service.SettingsService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "patentflow.lookup.google-patents.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AnnualFeeScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PatentMetadataRepository patentMetadataRepository;

    @Autowired
    private PatentReviewHistoryRepository reviewHistoryRepository;

    @Autowired
    private AnnualFeeAdjustmentRepository annualFeeAdjustmentRepository;

    @MockitoBean
    private AiReportAgentClient aiReportAgentClient;

    @MockitoBean
    private SettingsService settingsService;

    @Test
    void getAnnualFeeScheduleReturnsCountryAwareRows() throws Exception {
        mockMvc.perform(get("/api/v1/annual-fees/schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThan(100)))
                .andExpect(jsonPath("$.data[0].patentId").isNotEmpty())
                .andExpect(jsonPath("$.data[0].managementNumber").isNotEmpty())
                .andExpect(jsonPath("$.data[0].country").isNotEmpty())
                .andExpect(jsonPath("$.data[0].nextAnnualFeeDueDate").isNotEmpty())
                .andExpect(jsonPath("$.data[0].calculatedAnnualFeeDueDate").isNotEmpty())
                .andExpect(jsonPath("$.data[0].effectiveAnnualFeeDueDate").isNotEmpty())
                .andExpect(jsonPath("$.data[0].countryExtensionMonths").value(12))
                .andExpect(jsonPath("$.data[0].adjustmentHistory").isArray());
    }

    @Test
    void getAnnualFeeScheduleFiltersByCountry() throws Exception {
        mockMvc.perform(get("/api/v1/annual-fees/schedule")
                        .param("country", "KR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.data[0].country").value("KR"));
    }

    @Test
    void adjustAnnualFeeSchedulePersistsDateAndHistory() throws Exception {
        mockMvc.perform(patch("/api/v1/annual-fees/schedule/PAT-2026-0001")
                        .principal(adminPrincipal("이소율"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adjustedDueDate": "2026-08-15",
                                  "reason": "해외 패밀리 납부 일정과 맞추기 위해 조정",
                                  "adjustedBy": "요청 바디 조정자"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patentId").value("PAT-2026-0001"))
                // FEE-06: KR은 등록일 기준 — 기산일은 등록일(2026-02-25), 1~3년차 일괄 납부로
                // 계산 도래일은 4년차 납부일(등록일+3년)이다.
                .andExpect(jsonPath("$.data.annualFeeBaseDate").value("2026-02-25"))
                .andExpect(jsonPath("$.data.calculatedAnnualFeeDueDate").value("2029-02-25"))
                .andExpect(jsonPath("$.data.storedAnnualFeeDueDate").value("2026-08-15"))
                .andExpect(jsonPath("$.data.effectiveAnnualFeeDueDate").value("2026-08-15"))
                .andExpect(jsonPath("$.data.nextAnnualFeeDueDate").value("2026-08-15"))
                .andExpect(jsonPath("$.data.adjustedAnnualFeeDueDate").value("2026-08-15"))
                .andExpect(jsonPath("$.data.latestAdjustmentReason").value("해외 패밀리 납부 일정과 맞추기 위해 조정"))
                .andExpect(jsonPath("$.data.adjustmentHistory", hasSize(1)))
                .andExpect(jsonPath("$.data.adjustmentHistory[0].adjustedBy").value("이소율"));

        mockMvc.perform(get("/api/v1/patents/PAT-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeDueDate").value("2026-08-15"));

        LocalDate adjustedDueDate = LocalDate.parse("2026-08-15");
        assertThat(patentMetadataRepository.findById("PAT-2026-0001").orElseThrow().getFeeDueDate())
                .isEqualTo(adjustedDueDate);
        assertThat(reviewHistoryRepository.findByPatentIdOrderByCreatedAtDesc("PAT-2026-0001").get(0).getAnnualFeeDueDate())
                .isEqualTo(adjustedDueDate);

        List<AnnualFeeAdjustmentEntity> adjustments =
                annualFeeAdjustmentRepository.findByPatentIdOrderByAdjustedAtDesc("PAT-2026-0001");
        assertThat(adjustments).hasSize(1);
        assertThat(adjustments.get(0).getAdjustedDueDate()).isEqualTo(adjustedDueDate);
        assertThat(adjustments.get(0).getReason()).isEqualTo("해외 패밀리 납부 일정과 맞추기 위해 조정");
        assertThat(adjustments.get(0).getAdjustedBy()).isEqualTo("이소율");
    }

    @Test
    void adjustAnnualFeeScheduleRejectsDateAfterExpiration() throws Exception {
        mockMvc.perform(patch("/api/v1/annual-fees/schedule/PAT-2026-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adjustedDueDate": "2099-01-01",
                                  "reason": "잘못된 날짜"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void adjustAnnualFeeScheduleRejectsTooLongReason() throws Exception {
        mockMvc.perform(patch("/api/v1/annual-fees/schedule/PAT-2026-0001")
                        .principal(adminPrincipal("이소율"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adjustedDueDate": "2026-08-15",
                                  "reason": "%s"
                                }
                                """.formatted("가".repeat(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details.reason").isNotEmpty());
    }

    private UsernamePasswordAuthenticationToken adminPrincipal(String username) {
        return new UsernamePasswordAuthenticationToken(
                new UserPrincipalResponse(
                        "admin@test.com",
                        username,
                        List.of("ROLE_ADMIN"),
                        "USER-admin-test",
                        "ADMIN",
                        null,
                        null),
                null,
                AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
    }
}
