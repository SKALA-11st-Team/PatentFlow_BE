package com.syuuk.patentflow.patent.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.syuuk.patentflow.patent.dto.EvaluationCategory;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "patentflow.lookup.kipris.enabled=false",
        "patentflow.lookup.google-patents.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PatentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getPatentsReturnsPagedEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/patents")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.page.page").value(1))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(greaterThan(100)));
    }

    @Test
    void getPatentsCapsPageSizeAtTwenty() throws Exception {
        mockMvc.perform(get("/api/v1/patents")
                        .param("page", "1")
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(20)))
                .andExpect(jsonPath("$.page.size").value(20));
    }

    @Test
    void getPatentDetailSeparatesAiReportAndFinalDecision() throws Exception {
        mockMvc.perform(get("/api/v1/patents/PAT-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.managementNumber").value("P202405001-KR0"))
                .andExpect(jsonPath("$.data.title").value("상품 트렌드 예측을 반영한 강화학습 모델을 적용한 자산배분 시스템 및 방법"))
                .andExpect(jsonPath("$.data.aiEvaluationReport.recommendation").value("MAINTAIN"))
                .andExpect(jsonPath("$.data.finalDecisionRecord.decision").doesNotExist())
                .andExpect(jsonPath("$.data.aiEvaluationReport.scores[0].category").value(EvaluationCategory.RIGHTS.name()))
                .andExpect(jsonPath("$.data.aiEvaluationReport.scores[3].category").value(EvaluationCategory.LIFECYCLE_ECONOMICS.name()));
    }

    @Test
    void getPatentHistoryReturnsHistoryItems() throws Exception {
        mockMvc.perform(get("/api/v1/patents/PAT-2026-0001/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].type").value("AI_EVALUATION_CREATED"));
    }

    @Test
    void applyExecutiveApprovalUpdatesWorkflowAndDecision() throws Exception {
        mockMvc.perform(post("/api/v1/patents/executive-approvals/bulk-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patentIds": ["PAT-2026-0001"],
                                  "decision": "APPROVED_ABANDON"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("APPROVED_ABANDON"))
                .andExpect(jsonPath("$.data.updatedCount").value(1));

        mockMvc.perform(get("/api/v1/patents/PAT-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewWorkflowStatus").value("APPROVAL_COMPLETED"))
                .andExpect(jsonPath("$.data.executiveApprovalDecision").value("APPROVED_ABANDON"))
                .andExpect(jsonPath("$.data.legalActionResult").value("ABANDONED"));
    }

    @Test
    void getUnknownPatentReturnsNotFoundError() throws Exception {
        mockMvc.perform(get("/api/v1/patents/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATENT_NOT_FOUND"));
    }

    @Test
    void lookupBibliographicInfoReturnsMetadataFromDocument() throws Exception {
        mockMvc.perform(get("/api/v1/patents/external-lookup")
                        .param("managementNumber", "P202405001-KR0")
                        .param("sourcePriority", "KIPRIS,GOOGLE_PATENTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.managementNumber").value("P202405001-KR0"))
                .andExpect(jsonPath("$.data.applicationNumber").value("10-2024-0115774"))
                .andExpect(jsonPath("$.data.registrationNumber").value("10-2932891"))
                .andExpect(jsonPath("$.data.source").value("KIPRIS"));
    }

    @Test
    void lookupBibliographicInfoReturnsNullWhenNotMatched() throws Exception {
        mockMvc.perform(get("/api/v1/patents/external-lookup")
                        .param("managementNumber", "NO-MATCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void suggestContextReturnsClosestBusinessAndTechnologyArea() throws Exception {
        mockMvc.perform(post("/api/v1/patents/context-suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "managementNumber": "TEMP-001",
                                  "title": "블록체인 합의 과정 서명 검증 성능 향상",
                                  "productName": "ChainZ",
                                  "businessArea": "",
                                  "technologyArea": "",
                                  "applicationNumber": ""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessArea").value("Blockchain"))
                .andExpect(jsonPath("$.data.technologyArea").value("Blockchain"))
                .andExpect(jsonPath("$.data.confidenceText").exists())
                .andExpect(jsonPath("$.data.reason").exists());
    }

    @Test
    void getBusinessChecklistItemsReturnsDefinitions() throws Exception {
        mockMvc.perform(get("/api/v1/business/checklist-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[0].id").value("TECH_COMPLETENESS"))
                .andExpect(jsonPath("$.data[0].options", hasSize(4)));
    }

    @Test
    void submitBusinessChecklistCreatesSubmissionHistory() throws Exception {
        mockMvc.perform(post("/api/v1/patents/PAT-2026-0001/business-submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patentId": "PAT-2026-0001",
                                  "evaluatorName": "R&D본부",
                                  "evaluatedAt": "2026-05-06",
                                  "responses": [
                                    {
                                      "itemId": "TECH_COMPLETENESS",
                                      "score": 4,
                                      "aiSuggestedScore": 3,
                                      "memo": "제품 적용 가능성이 확인됩니다."
                                    },
                                    {
                                      "itemId": "TECH_ORIGINALITY",
                                      "score": 3,
                                      "aiSuggestedScore": 3,
                                      "memo": "기술 차별성이 일부 확인됩니다."
                                    }
                                  ],
                                  "qualitativeScore": 2,
                                  "qualitativeMemo": "사업부 정성 검토",
                                  "finalOpinion": "MAINTAIN",
                                  "finalReason": "현재 사업 적용 가능성이 있습니다.",
                                  "additionalNeeds": "추가 시장 자료"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalOpinion").value("MAINTAIN"))
                .andExpect(jsonPath("$.data.responses", hasSize(2)));

        mockMvc.perform(get("/api/v1/patents/PAT-2026-0001/business-submissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].submissionId").value("PAT-2026-0001-SUB-01"))
                .andExpect(jsonPath("$.data[0].decision").value("MAINTAIN"))
                .andExpect(jsonPath("$.data[0].checklistTotal").value(9))
                .andExpect(jsonPath("$.data[0].aiRecommendation").value("MAINTAIN"));

        mockMvc.perform(get("/api/v1/patents/PAT-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewWorkflowStatus").value("BUSINESS_RESPONSE_RECEIVED"))
                .andExpect(jsonPath("$.data.businessOpinionDecision").value("MAINTAIN"))
                .andExpect(jsonPath("$.data.businessOpinion.decision").value("MAINTAIN"));
    }

                @Test
                void submitBusinessChecklistRejectsMismatchedPatentId() throws Exception {
                                mockMvc.perform(post("/api/v1/patents/PAT-2026-0001/business-submissions")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "patentId": "PAT-2026-0002",
                                                                                                                                        "responses": [],
                                                                                                                                        "qualitativeScore": 0,
                                                                                                                                        "finalOpinion": "MAINTAIN"
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isBadRequest())
                                                                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
                }

    @Test
    void getBusinessSubmissionsForUnknownPatentReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/patents/UNKNOWN/business-submissions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATENT_NOT_FOUND"));
    }

    @Test
    void sendMailingMovesPatentsToWaitingBusinessResponse() throws Exception {
        mockMvc.perform(post("/api/v1/mailings/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patentIds": ["PAT-2026-0001"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(1))
                .andExpect(jsonPath("$.data.updatedPatentIds[0]").value("PAT-2026-0001"));

        mockMvc.perform(get("/api/v1/patents/PAT-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewWorkflowStatus").value("WAITING_BUSINESS_RESPONSE"));
    }

    @Test
    void createAndUpdatePatentBasicInformation() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/patents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "managementNumber": "SKAX-NEW",
                                  "title": "신규 테스트 특허",
                                  "applicationDate": "2022-01-02",
                                  "coApplicants": "없음",
                                  "country": "KR",
                                  "registrationDate": "2024-01-02",
                                  "applicationNumber": "10-2022-0000004",
                                  "registrationNumber": "10-2600000",
                                  "expectedExpirationDate": "2042-01-02",
                                  "source": "KIPRIS",
                                  "businessArea": "AI",
                                  "technologyArea": "문서처리",
                                  "productName": "PatentFlow"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("CREATED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String patentId = JsonPath.read(createResponse, "$.data.patentId");

        mockMvc.perform(put("/api/v1/patents/{patentId}", patentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "managementNumber": "SKAX-NEW",
                                  "title": "수정된 테스트 특허",
                                  "applicationDate": "2022-01-02",
                                  "coApplicants": "없음",
                                  "country": "KR",
                                  "registrationDate": "2024-01-02",
                                  "applicationNumber": "10-2022-0000004",
                                  "registrationNumber": "10-2600000",
                                  "expectedExpirationDate": "2042-01-02",
                                  "source": "KIPRIS",
                                  "businessArea": "AI",
                                  "technologyArea": "문서처리",
                                  "productName": "PatentFlow"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("UPDATED"));

        mockMvc.perform(get("/api/v1/patents/{patentId}", patentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 테스트 특허"));
    }
}
