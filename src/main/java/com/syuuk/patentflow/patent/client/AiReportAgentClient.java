package com.syuuk.patentflow.patent.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
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

    public AgentEvaluateResponse evaluate(String patentId) {
        return doEvaluate(patentId, TIMEOUT);
    }

    // 배치 자동 생성 전용 — 에이전트 응답에 10분 이상 소요될 수 있으므로 타임아웃을 20분으로 설정
    public AgentEvaluateResponse evaluateForBatch(String patentId) {
        return doEvaluate(patentId, BATCH_TIMEOUT);
    }

    private AgentEvaluateResponse doEvaluate(String patentId, Duration timeout) {
        try {
            String url = agentUrl + "/api/v1/ai/patents/" + patentId + "/evaluate";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("FastAPI evaluate returned {} for patent {}", response.statusCode(), patentId);
                throw new PatentFlowException(ErrorCode.AI_REPORT_FAILED);
            }
            return objectMapper.readValue(response.body(), AgentEvaluateResponse.class);
        } catch (PatentFlowException e) {
            throw e;
        } catch (Exception e) {
            log.warn("FastAPI evaluate failed for patent {} (timeout={}): {}", patentId, timeout, e.getMessage());
            throw new PatentFlowException(ErrorCode.AI_REPORT_FAILED);
        }
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
            OffsetDateTime generatedAt
    ) {
        public String summaryText() {
            return summaryMarkdown;
        }

        public String reportMarkdown() {
            if (rawMarkdown != null && !rawMarkdown.isBlank()) {
                return rawMarkdown;
            }
            return summaryMarkdown;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentScoreItem(String category, Integer score, String evidence) {}
}
