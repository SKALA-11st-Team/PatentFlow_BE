package com.syuuk.patentflow.patent.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.syuuk.patentflow.auth.dto.UserPrincipalResponse;
import com.syuuk.patentflow.patent.client.AiReportAgentClient;
import com.syuuk.patentflow.patent.client.AiReportAgentClient.AgentEvaluateResponse;
import com.syuuk.patentflow.patent.client.AiReportAgentClient.AgentScoreItem;
import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import com.syuuk.patentflow.patent.domain.PatentReviewHistoryEntity;
import com.syuuk.patentflow.patent.dto.EvaluationCategory;
import com.syuuk.patentflow.patent.dto.ReviewWorkflowStatus;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
        "patentflow.lookup.google-patents.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PatentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiReportAgentClient aiReportAgentClient;

    @Autowired
    private PatentMetadataRepository patentMetadataRepository;

    @Autowired
    private PatentReviewHistoryRepository reviewHistoryRepository;

    private RequestPostProcessor businessAuth(String departmentId, String departmentName) {
        UserPrincipalResponse principal = new UserPrincipalResponse(
                departmentId.toLowerCase() + "@syuuk.test",
                departmentName + " 담당자",
                List.of("ROLE_BUSINESS"),
                "USER-" + departmentId,
                "BUSINESS",
                departmentId,
                departmentName);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_BUSINESS")));
        return request -> {
            request.setUserPrincipal(authentication);
            return request;
        };
    }

    private RequestPostProcessor adminAuth() {
        UserPrincipalResponse principal = new UserPrincipalResponse(
                "admin@syuuk.test",
                "관리자",
                List.of("ROLE_ADMIN"),
                "USER-admin",
                "ADMIN",
                null,
                null);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        return request -> {
            request.setUserPrincipal(authentication);
            return request;
        };
    }

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
    void getPatentsCapsPageSizeAtOneHundred() throws Exception {
        mockMvc.perform(get("/api/v1/patents")
                .param("page", "1")
                .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(100)))
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    void getPatentDetailSeparatesAiReportAndFinalDecision() throws Exception {
        mockMvc.perform(get("/api/v1/patents/PAT-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.managementNumber").value("P202405001-KR0"))
                .andExpect(jsonPath("$.data.title").value("상품 트렌드 예측을 반영한 강화학습 모델을 적용한 자산배분 시스템 및 방법"))
                .andExpect(jsonPath("$.data.aiEvaluationReport.recommendation").value("MAINTAIN"))
                .andExpect(jsonPath("$.data.finalDecisionRecord.decision").doesNotExist())
                .andExpect(jsonPath("$.data.aiEvaluationReport.scores[0].category")
                        .value(EvaluationCategory.RIGHTS.name()))
                .andExpect(jsonPath("$.data.aiEvaluationReport.scores[3].category")
                        .value(EvaluationCategory.BUSINESS_ALIGNMENT.name()))
                .andExpect(jsonPath("$.data.aiEvaluationReport.scores", hasSize(4)));
    }

    @Test
    void getPatentHistoryReturnsHistoryItems() throws Exception {
        mockMvc.perform(get("/api/v1/patents/PAT-2026-0001/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].historyId").value("PAT-2026-0001|DEMO-SEED-WORKFLOW"))
                .andExpect(jsonPath("$.data[0].type").value("WORKFLOW_STATUS_UPDATED"))
                .andExpect(jsonPath("$.data[1].historyId").value("PAT-2026-0001|DEMO-SEED-AI"))
                .andExpect(jsonPath("$.data[1].type").value("AI_EVALUATION_CREATED"));
    }

    @Test
    void requestAiReportCreatesAsyncJob() throws Exception {
        when(aiReportAgentClient.evaluateForBatch(org.mockito.ArgumentMatchers.eq("PAT-2026-0001"),
                org.mockito.ArgumentMatchers.any())).thenReturn(new AgentEvaluateResponse(
                "PAT-2026-0001",
                List.of(new AgentScoreItem("권리성", 82, "A", "청구항 보호 범위가 명확합니다.", List.of())),
                "MAINTAIN",
                null,
                "## AI 평가 레포트\n\n유지 검토가 가능합니다.",
                82,
                82.0,
                "A",
                "유지 권고",
                null,
                false,
                null,
                OffsetDateTime.parse("2026-05-22T00:00:00Z"),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                null
        ));

        mockMvc.perform(post("/api/v1/patents/PAT-2026-0001/request-ai-report"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.jobId").exists())
                .andExpect(jsonPath("$.data.patentId").value("PAT-2026-0001"));

        mockMvc.perform(get("/api/v1/patents/PAT-2026-0001/ai-report/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patentId").value("PAT-2026-0001"));
    }

    @Test
    void recordFinalDecisionPersistsDecisionAndLegalAction() throws Exception {
        // PAT-2026-0005는 공동출원 특허라 최종 판단 전 공동출원인 합의(AGREED)가 선행돼야 한다.
        mockMvc.perform(post("/api/v1/patents/PAT-2026-0005/co-applicant-consent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"AGREED","reason":"공동출원인 합의 완료"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/patents/PAT-2026-0005/final-decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "legalActionResult": "MAINTAINED",
                          "reason": "사업부 의견과 AI 평가를 종합해 유지합니다."
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patentId").value("PAT-2026-0005"))
                .andExpect(jsonPath("$.data.reviewWorkflowStatus").value("NOT_IN_REVIEW"))
                .andExpect(jsonPath("$.data.legalActionResult").value("MAINTAINED"))
                .andExpect(jsonPath("$.data.finalDecisionRecord.reason").value("사업부 의견과 AI 평가를 종합해 유지합니다."));

        mockMvc.perform(get("/api/v1/patents/PAT-2026-0005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewWorkflowStatus").value("NOT_IN_REVIEW"))
                .andExpect(jsonPath("$.data.legalActionResult").value("MAINTAINED"))
                .andExpect(jsonPath("$.data.lifecycleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.finalDecisionRecord.reason").value("사업부 의견과 AI 평가를 종합해 유지합니다."));
    }

    @Test
    void recordFinalDecisionRejectsRemovedSalesState() throws Exception {
        mockMvc.perform(post("/api/v1/patents/PAT-2026-0005/final-decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "legalActionResult": "SOLD",
                          "reason": "매각 상태는 사용하지 않습니다."
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
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
                .param("applicationNumber", "10-2024-0115774")
                .param("sourcePriority", "KIPRIS,GOOGLE_PATENTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.managementNumber").value("P202405001-KR0"))
                .andExpect(jsonPath("$.data.applicationNumber").value("10-2024-0115774"))
                .andExpect(jsonPath("$.data.registrationNumber").value("10-2932891"))
                .andExpect(jsonPath("$.data.source").value("INTERNAL_METADATA"))
                .andExpect(jsonPath("$.data.lookupStatus").value("SOURCE_UNCONFIGURED"))
                .andExpect(jsonPath("$.data.sourceConfidence").value("LOW"))
                .andExpect(jsonPath("$.data.lookupMessage").exists());
    }

    @Test
    void lookupBibliographicInfoDistinguishesNotFoundFromSourceError() throws Exception {
        mockMvc.perform(get("/api/v1/patents/external-lookup")
                .param("managementNumber", "NO-MATCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.managementNumber").value("NO-MATCH"))
                .andExpect(jsonPath("$.data.lookupStatus").value("SOURCE_UNCONFIGURED"))
                .andExpect(jsonPath("$.data.sourceConfidence").value("NONE"));
    }

    @Test
    void lookupBibliographicInfoPreservesKrRegistrationNumberFormatting() throws Exception {
        mockMvc.perform(get("/api/v1/patents/external-lookup")
                .param("registrationNumber", "102932891")
                .param("sourcePriority", "KIPRIS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.managementNumber").value("P202405001-KR0"))
                .andExpect(jsonPath("$.data.registrationNumber").value("10-2932891"));
    }

    @Test
    void getReviewTargetsFiltersByQuarterCountryAndDateInDatabaseBackedPath() throws Exception {
        String response = mockMvc.perform(get("/api/v1/patents/review-targets")
                .param("quarter", "Q3")
                .param("country", "KR")
                .param("dateFrom", "2026-08-01")
                .param("dateTo", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> countries = JsonPath.read(response, "$.data[*].country");
        List<String> managementNumbers = JsonPath.read(response, "$.data[*].managementNumber");
        org.assertj.core.api.Assertions.assertThat(countries).containsOnly("KR");
        org.assertj.core.api.Assertions.assertThat(managementNumbers).contains("P202405001-KR0");
    }

    @Test
    void getPatentsAppliesCountryAndInReviewFiltersServerSide() throws Exception {
        // CONTRACT-09/DASH-08: 페이징 엔드포인트가 국가/검토여부를 DB 레벨에서 필터링한다.
        String krResponse = mockMvc.perform(get("/api/v1/patents")
                .param("country", "KR")
                .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> countries = JsonPath.read(krResponse, "$.data[*].country");
        org.assertj.core.api.Assertions.assertThat(countries).isNotEmpty().containsOnly("KR");

        String inReviewResponse = mockMvc.perform(get("/api/v1/patents")
                .param("inReview", "true")
                .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Boolean> inReviewFlags = JsonPath.read(inReviewResponse, "$.data[*].inReview");
        // 시드에 inReview=true 가 없을 수 있으므로(빈 결과 허용) false 누출만 없으면 필터가 동작한 것이다.
        org.assertj.core.api.Assertions.assertThat(inReviewFlags).doesNotContain(false);
    }

    @Test
    void filterOptionsExposesDistinctValuesAndDrivesServerContextFilter() throws Exception {
        // CONTRACT-09/DASH-08: 드롭다운 옵션(전체 distinct) + 그 값으로 서버 영역 필터가 동작한다.
        String options = mockMvc.perform(get("/api/v1/patents/filter-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.countries", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.businessAreas", hasSize(greaterThan(0))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(JsonPath.<List<String>>read(options, "$.data.countries"))
                .contains("KR");

        String firstBusinessArea = JsonPath.<List<String>>read(options, "$.data.businessAreas").get(0);
        String filtered = mockMvc.perform(get("/api/v1/patents")
                .param("businessArea", firstBusinessArea)
                .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> businessAreas = JsonPath.read(filtered, "$.data[*].businessArea");
        org.assertj.core.api.Assertions.assertThat(businessAreas).isNotEmpty().containsOnly(firstBusinessArea);
    }

    private static final String MAINTAIN_BODY = """
            {"legalActionResult":"MAINTAINED","reason":"유지 검토"}""";

    private void forceWorkflowStatus(String patentId, ReviewWorkflowStatus status) {
        PatentReviewHistoryEntity history =
                reviewHistoryRepository.findByPatentIdOrderByCreatedAtDesc(patentId).get(0);
        history.setReviewWorkflowStatus(status);
        reviewHistoryRepository.save(history);
    }

    private String firstPatentId(boolean joint) {
        return patentMetadataRepository.findAll(org.springframework.data.domain.Sort.by("patentId")).stream()
                .filter(entity -> joint == "Y".equalsIgnoreCase(entity.getJointApplication()))
                .map(PatentMetadataEntity::getPatentId)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void coApplicantPatentBlocksFinalDecisionUntilConsentAgreed() throws Exception {
        String patentId = firstPatentId(true);
        forceWorkflowStatus(patentId, ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED);

        mockMvc.perform(get("/api/v1/patents/{id}", patentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jointApplication").value(true));

        // 1. 합의 없이 최종 판단 → 차단(409)
        mockMvc.perform(post("/api/v1/patents/{id}/final-decision", patentId)
                .with(adminAuth()).contentType(MediaType.APPLICATION_JSON).content(MAINTAIN_BODY))
                .andExpect(status().isConflict());

        // 2. 공동출원인 합의 기록(AGREED)
        mockMvc.perform(post("/api/v1/patents/{id}/co-applicant-consent", patentId)
                .with(adminAuth()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"AGREED","reason":"연차료 분담 합의 완료"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jointApplication").value(true))
                .andExpect(jsonPath("$.data.coApplicantConsent.status").value("AGREED"));

        // 3. 합의 후 최종 판단 성공
        mockMvc.perform(post("/api/v1/patents/{id}/final-decision", patentId)
                .with(adminAuth()).contentType(MediaType.APPLICATION_JSON).content(MAINTAIN_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void coApplicantDisagreeStillBlocksFinalDecision() throws Exception {
        String patentId = firstPatentId(true);
        forceWorkflowStatus(patentId, ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED);

        mockMvc.perform(post("/api/v1/patents/{id}/co-applicant-consent", patentId)
                .with(adminAuth()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"DISAGREED","reason":"공동출원인 반대"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coApplicantConsent.status").value("DISAGREED"));

        mockMvc.perform(post("/api/v1/patents/{id}/final-decision", patentId)
                .with(adminAuth()).contentType(MediaType.APPLICATION_JSON).content(MAINTAIN_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void nonJointPatentFinalDecisionSkipsConsentGate() throws Exception {
        String patentId = firstPatentId(false);
        forceWorkflowStatus(patentId, ReviewWorkflowStatus.BUSINESS_RESPONSE_RECEIVED);

        mockMvc.perform(post("/api/v1/patents/{id}/final-decision", patentId)
                .with(adminAuth()).contentType(MediaType.APPLICATION_JSON).content(MAINTAIN_BODY))
                .andExpect(status().isOk());
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
        mockMvc.perform(post("/api/v1/patents/PAT-2026-0003/business-submissions")
                .with(businessAuth("DEPT-MFG", "제조사업부"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "patentId": "PAT-2026-0003",
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
                            },
                            {
                              "itemId": "MARKETABILITY",
                              "score": 3,
                              "aiSuggestedScore": 3,
                              "memo": "제조 분야 적용 시장성이 있습니다."
                            },
                            {
                              "itemId": "EXPECTED_EFFECT",
                              "score": 2,
                              "aiSuggestedScore": 3,
                              "memo": "운영 효율화 효과는 추가 확인이 필요합니다."
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
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.responses", hasSize(4)));

        mockMvc.perform(get("/api/v1/patents/PAT-2026-0003/business-submissions")
                .with(adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].submissionId").value("PAT-2026-0003-SUB-01"))
                .andExpect(jsonPath("$.data[0].decision").value("MAINTAIN"))
                .andExpect(jsonPath("$.data[0].version").value(1))
                .andExpect(jsonPath("$.data[0].checklistTotal").value(14))
                .andExpect(jsonPath("$.data[0].aiRecommendation").value("ABANDON"));

        mockMvc.perform(get("/api/v1/patents/PAT-2026-0003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewWorkflowStatus").value("BUSINESS_RESPONSE_RECEIVED"))
                .andExpect(jsonPath("$.data.businessOpinionDecision").value("MAINTAIN"))
                .andExpect(jsonPath("$.data.businessOpinion.decision").value("MAINTAIN"));

        mockMvc.perform(post("/api/v1/patents/PAT-2026-0003/business-submissions")
                .with(businessAuth("DEPT-MFG", "제조사업부"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "patentId": "PAT-2026-0003",
                          "responses": [
                            {"itemId": "TECH_COMPLETENESS", "score": 4, "aiSuggestedScore": 3},
                            {"itemId": "TECH_ORIGINALITY", "score": 3, "aiSuggestedScore": 3},
                            {"itemId": "MARKETABILITY", "score": 3, "aiSuggestedScore": 3},
                            {"itemId": "EXPECTED_EFFECT", "score": 2, "aiSuggestedScore": 3}
                          ],
                          "qualitativeScore": 2,
                          "finalOpinion": "MAINTAIN"
                        }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_WORKFLOW_STATUS"));
    }

    @Test
    void submitBusinessChecklistRejectsInvalidWorkflowStatus() throws Exception {
        mockMvc.perform(post("/api/v1/patents/PAT-2026-0001/business-submissions")
                .with(businessAuth("DEPT-RND", "R&D본부"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "patentId": "PAT-2026-0001",
                          "responses": [
                            {"itemId": "TECH_COMPLETENESS", "score": 4, "aiSuggestedScore": 3},
                            {"itemId": "TECH_ORIGINALITY", "score": 3, "aiSuggestedScore": 3},
                            {"itemId": "MARKETABILITY", "score": 3, "aiSuggestedScore": 3},
                            {"itemId": "EXPECTED_EFFECT", "score": 2, "aiSuggestedScore": 3}
                          ],
                          "qualitativeScore": 0,
                          "finalOpinion": "MAINTAIN"
                        }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_WORKFLOW_STATUS"));
    }

    @Test
    void submitBusinessChecklistRejectsMismatchedPatentId() throws Exception {
        mockMvc.perform(post("/api/v1/patents/PAT-2026-0001/business-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "patentId": "PAT-2026-0002",
                          "responses": [
                            {
                              "itemId": "TECH_COMPLETENESS",
                              "score": 4,
                              "aiSuggestedScore": 3,
                              "memo": "제품 적용 가능성이 확인됩니다."
                            }
                          ],
                          "qualitativeScore": 0,
                          "finalOpinion": "MAINTAIN"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void submitBusinessChecklistRejectsMissingChecklistDefinitionItem() throws Exception {
        mockMvc.perform(post("/api/v1/patents/PAT-2026-0003/business-submissions")
                .with(businessAuth("DEPT-MFG", "제조사업부"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "patentId": "PAT-2026-0003",
                          "responses": [
                            {"itemId": "TECH_COMPLETENESS", "score": 4, "aiSuggestedScore": 3},
                            {"itemId": "TECH_ORIGINALITY", "score": 3, "aiSuggestedScore": 3},
                            {"itemId": "MARKETABILITY", "score": 3, "aiSuggestedScore": 3}
                          ],
                          "qualitativeScore": 0,
                          "finalOpinion": "MAINTAIN"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void getBusinessSubmissionsRejectsMissingAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/patents/PAT-2026-0003/business-submissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void getBusinessSubmissionsForUnknownPatentReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/patents/UNKNOWN/business-submissions")
                .with(adminAuth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATENT_NOT_FOUND"));
    }

    @Test
    void sendMailingWithoutCredentialsRecordsHistoryWithoutWorkflowTransition() throws Exception {
        mockMvc.perform(post("/api/v1/mailings/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "drafts": [
                            {
                              "recipientEmail": "rnd.manager@syuuk.test",
                              "recipientName": "R&D 담당자",
                              "subject": "PatentFlow 사업부 검토 요청",
                              "body": "검토 요청드립니다.",
                              "patents": [
                                {
                                  "patentId": "PAT-2026-0001",
                                  "managementNumber": "P202405001-KR0",
                                  "originalPatentUrl": "https://patents.google.com/patent/KR102932891/ko",
                                  "title": "상품 트렌드 예측을 반영한 강화학습 모델을 적용한 자산배분 시스템 및 방법"
                                },
                                {
                                  "patentId": "PAT-2026-0002",
                                  "managementNumber": "P202405002-KR0",
                                  "originalPatentUrl": "https://patents.google.com/patent/KR102932892/ko",
                                  "title": "테스트 특허"
                                }
                              ]
                            }
                          ]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(0))
                .andExpect(jsonPath("$.data.recordedCount").value(1));

        mockMvc.perform(get("/api/v1/patents/PAT-2026-0002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewWorkflowStatus").value("MAIL_READY"));

        mockMvc.perform(get("/api/v1/mailings/history")
                .param("patentId", "PAT-2026-0002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("RECORDED"))
                .andExpect(jsonPath("$.data[0].recipientEmail").value("rnd.manager@syuuk.test"));
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
                .andExpect(jsonPath("$.data.patentId").value("PAT-2026-0186"))
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
