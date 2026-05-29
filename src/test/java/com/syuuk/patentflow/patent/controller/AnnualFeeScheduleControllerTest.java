package com.syuuk.patentflow.patent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.syuuk.patentflow.patent.client.AiReportAgentClient;
import com.syuuk.patentflow.patent.domain.AnnualFeeAdjustmentEntity;
import com.syuuk.patentflow.patent.repository.AnnualFeeAdjustmentRepository;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

    @Test
    void getAnnualFeeScheduleReturnsCountryAwareRows() throws Exception {
        mockMvc.perform(get("/api/v1/annual-fees/schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThan(100)))
                .andExpect(jsonPath("$.data[0].patentId").isNotEmpty())
                .andExpect(jsonPath("$.data[0].managementNumber").isNotEmpty())
                .andExpect(jsonPath("$.data[0].country").isNotEmpty())
                .andExpect(jsonPath("$.data[0].nextAnnualFeeDueDate").isNotEmpty())
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adjustedDueDate": "2026-08-15",
                                  "reason": "해외 패밀리 납부 일정과 맞추기 위해 조정",
                                  "adjustedBy": "테스트 관리자"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patentId").value("PAT-2026-0001"))
                .andExpect(jsonPath("$.data.nextAnnualFeeDueDate").value("2026-08-15"))
                .andExpect(jsonPath("$.data.adjustedAnnualFeeDueDate").value("2026-08-15"))
                .andExpect(jsonPath("$.data.latestAdjustmentReason").value("해외 패밀리 납부 일정과 맞추기 위해 조정"))
                .andExpect(jsonPath("$.data.adjustmentHistory", hasSize(1)))
                .andExpect(jsonPath("$.data.adjustmentHistory[0].adjustedBy").value("테스트 관리자"));

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
        assertThat(adjustments.get(0).getAdjustedBy()).isEqualTo("테스트 관리자");
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
}
