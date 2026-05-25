package com.syuuk.patentflow.patent.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

    @Value("${agent.url:http://patentflow-agent:8000}")
    private String agentUrl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiReportAgentClient(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.objectMapper = objectMapper;
    }

    public AgentEvaluateResponse evaluate(String patentId) {
        try {
            String url = agentUrl + "/api/v1/ai/patents/" + patentId + "/evaluate";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("FastAPI evaluate returned {} for patent {}", response.statusCode(), patentId);
                return fallback(patentId);
            }
            return objectMapper.readValue(response.body(), AgentEvaluateResponse.class);
        } catch (Exception e) {
            log.warn("FastAPI evaluate failed for patent {}: {}", patentId, e.getMessage());
            return fallback(patentId);
        }
    }

    private AgentEvaluateResponse fallback(String patentId) {
        return new AgentEvaluateResponse(
                patentId,
                "AI 평가 서비스 연결 실패 - 기본 응답",
                List.of(),
                "HOLD",
                null,
                "",
                OffsetDateTime.now()
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentEvaluateResponse(
            String patentId,
            String summary,
            List<AgentScoreItem> scores,
            String recommendation,
            String summaryMarkdown,
            String rawMarkdown,
            OffsetDateTime generatedAt
    ) {
        public String summaryText() {
            if (summary != null && !summary.isBlank()) {
                return summary;
            }
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
