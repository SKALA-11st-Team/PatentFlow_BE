package com.syuuk.patentflow.patent.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AiReportAgentClient {

    private static final Logger log = LoggerFactory.getLogger(AiReportAgentClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration BATCH_TIMEOUT = Duration.ofMinutes(20);

    @Value("${agent.url:http://patentflow-agent:8000}")
    private String agentUrl;

    // SEC-01: agent 인바운드 인증 키. 설정 시 모든 agent 호출에 X-API-Key로 동봉한다(미설정 시 미동봉).
    // agent 측 AGENT_INBOUND_API_KEY와 동일 값이어야 한다(relaxed binding: AGENT_INBOUND_API_KEY).
    @Value("${agent.inbound-api-key:}")
    private String agentInboundApiKey;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiReportAgentClient(ObjectMapper objectMapper) {
        // uvicorn(FastAPI)이 HTTP/2 upgrade를 지원하지 않으므로 HTTP_1_1로 고정
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.objectMapper = objectMapper;
    }

    // SEC-01: 인바운드 키가 설정된 경우에만 X-API-Key 헤더를 동봉한다(미설정 시 미동봉 — 기존 배포 호환).
    private void applyAgentAuth(HttpRequest.Builder builder) {
        if (agentInboundApiKey != null && !agentInboundApiKey.isBlank()) {
            builder.header("X-API-Key", agentInboundApiKey);
        }
    }

    public AgentEvaluateResponse evaluate(String patentId) {
        return doEvaluate(patentId, TIMEOUT, null);
    }

    /** @param valuationConfig 가치평가 기준(계약 C1 valuationConfig). null이면 agent 기본값으로 평가. */
    public AgentEvaluateResponse evaluate(String patentId, Object valuationConfig) {
        return doEvaluate(patentId, TIMEOUT, valuationConfig);
    }

    // 배치 자동 생성 전용 — 에이전트 응답에 10분 이상 소요될 수 있으므로 타임아웃을 20분으로 설정
    public AgentEvaluateResponse evaluateForBatch(String patentId) {
        return doEvaluate(patentId, BATCH_TIMEOUT, null);
    }

    public AgentEvaluateResponse evaluateForBatch(String patentId, Object valuationConfig) {
        return doEvaluate(patentId, BATCH_TIMEOUT, valuationConfig);
    }

    private AgentEvaluateResponse doEvaluate(String patentId, Duration timeout, Object valuationConfig) {
        try {
            String url = agentUrl + "/api/v1/ai/patents/" + patentId + "/evaluate";
            // 구 agent는 valuationConfig를 모르는 필드로 무시한다(pydantic extra ignore) — 양방향 호환.
            String body = valuationConfig == null
                    ? "{}"
                    : objectMapper.writeValueAsString(java.util.Map.of("valuationConfig", valuationConfig));
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            applyAgentAuth(builder);
            HttpRequest request = builder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("FastAPI evaluate returned {} for patent {}", response.statusCode(), patentId);
                return fallback(patentId, "AI 평가 서비스가 오류 응답을 반환했습니다. status=" + response.statusCode());
            }
            return objectMapper.readValue(response.body(), AgentEvaluateResponse.class);
        } catch (Exception e) {
            log.warn("FastAPI evaluate failed for patent {} (timeout={}): {}", patentId, timeout, e.getMessage());
            return fallback(patentId, "AI 평가 서비스 연결 실패: " + e.getMessage());
        }
    }

    private AgentEvaluateResponse fallback(String patentId, String failureReason) {
        return new AgentEvaluateResponse(
                patentId,
                List.of(),
                "HOLD",
                "AI 평가 서비스 연결 실패 - 기본 응답",
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                failureReason,
                OffsetDateTime.now(),
                // ORCH-06/AIREPORT-02: 폴백은 리치 근거가 없으므로 빈 값.
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                null
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentEvaluateResponse(
            String patentId,
            List<AgentScoreItem> scores,
            String recommendation,
            String summaryMarkdown,
            // Agent는 "valuationReportMarkdown" 필드명으로 전체 평가 레포트를 반환한다
            @JsonProperty("valuationReportMarkdown") String rawMarkdown,
            Integer totalScore,
            Double averageScore,
            String finalGrade,
            String finalIndicator,
            String artifactDir,
            Boolean degraded,
            String failureReason,
            OffsetDateTime generatedAt,
            // ORCH-06/AIREPORT-02: 에이전트가 산출하는 리포트 레벨 리치 근거(그동안 record 미정의로 폐기되던 필드).
            List<String> missingInformation,
            String keyEvidence,
            List<String> judgementGrounds,
            List<String> businessCheckRequests,
            List<AgentSourceRef> externalSources,
            // 계약 C1: agent가 실제 적용한 가치평가 기준 스냅샷(source=request|default). 구 agent는 null.
            java.util.Map<String, Object> appliedValuationConfig
    ) {
        public String summaryText() {
            return summaryMarkdown;
        }

        public String reportMarkdown() {
            // 전체 평가 레포트가 없을 때 요약문(summaryMarkdown)으로 폴백하면 요약이 레포트로 둔갑해
            // 표시된다. 폴백은 PatentWorkflowService.normalizeMarkdown 한 곳에서 합성 레포트(요약+점수+권고)
            // 형태로만 수행한다.
            return rawMarkdown != null && !rawMarkdown.isBlank() ? rawMarkdown : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentScoreItem(
            String category,
            Integer score,
            String grade,
            String evidence,
            // ORCH-06/AIREPORT-02: 축별 세부 근거(클릭형 출처 포함).
            List<EvidenceDetailItem> evidenceDetails
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvidenceDetailItem(String text, AgentSourceRef source) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentSourceRef(String title, String url) {}

    /**
     * 특허 분야 추천 — 관리자 분류(taxonomy)를 함께 보내 에이전트에 추천을 요청한다.
     * 비정상 응답/실패 시 null을 반환해 호출 측이 기존 in-memory 추천으로 폴백하도록 한다.
     */
    public AgentFieldRecommendation recommendFields(String patentId, Object requestBody) {
        try {
            String url = agentUrl + "/api/v1/ai/patents/" + patentId + "/recommend-fields";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)));
            applyAgentAuth(builder);
            HttpRequest request = builder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("FastAPI recommend-fields returned {} for patent {}", response.statusCode(), patentId);
                return null;
            }
            return objectMapper.readValue(response.body(), AgentFieldRecommendation.class);
        } catch (Exception e) {
            log.warn("FastAPI recommend-fields failed for patent {}: {}", patentId, e.getMessage());
            return null;
        }
    }

    // CONTRACT-04: 에이전트 분야 추천 응답은 사업/기술 분야만 반환한다. productName은 에이전트가
    // 산출하지 않고 BE에서 읽는 곳도 없던 dead 필드여서 제거해 계약을 응답 실제와 일치시킨다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentFieldRecommendation(
            String businessArea,
            String technologyArea,
            Double confidence,
            String confidenceText,
            String reason
    ) {}
}
