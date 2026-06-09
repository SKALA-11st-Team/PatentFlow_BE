package com.syuuk.patentflow.patent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.patent.client.AiReportAgentClient;
import com.syuuk.patentflow.patent.client.AiReportAgentClient.AgentEvaluateResponse;
import com.syuuk.patentflow.patent.client.AiReportAgentClient.AgentScoreItem;
import com.syuuk.patentflow.patent.dto.AiEvaluationReportResponse;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * VAL-09: 4축 모두 0점인 실제 매각 후보(totalScore=0)와 점수 미산출(totalScore=null)을 구분하는지 검증한다.
 * 과거 회귀(totalScore == 0 ? null)로 0점이 '미산출'과 섞이지 않도록 락한다. degraded가 명시 구분 플래그다.
 */
class PatentWorkflowServiceMapAgentResponseTest {

    private PatentWorkflowService service;

    @BeforeEach
    void setUp() {
        AiReportStorageService storage = mock(AiReportStorageService.class);
        when(storage.storeMarkdown(any(), any(), any())).thenReturn("/tmp/report.md");
        service = new PatentWorkflowService(
                mock(PatentReviewService.class),
                mock(PatentMetadataRepository.class),
                mock(PatentReviewHistoryRepository.class),
                mock(AiReportAgentClient.class),
                storage,
                mock(AnnualFeeScheduleService.class),
                new ObjectMapper());
    }

    private AgentScoreItem score(String category, Integer value) {
        return new AgentScoreItem(category, value, value == null ? null : "D", "근거", List.of());
    }

    private AgentEvaluateResponse agentResponse(List<AgentScoreItem> scores, Integer totalScore) {
        return new AgentEvaluateResponse(
                "PAT-VAL09", scores, "포기 검토", null, "# 보고서",
                totalScore, null, "D", "포기 검토", null, false, null, OffsetDateTime.now(),
                List.of(), null, List.of(), List.of(), List.of());
    }

    @Test
    void allZeroScoresArePreservedAsRealCandidateNotNull() {
        AgentEvaluateResponse agent = agentResponse(
                List.of(score("권리성", 0), score("기술성", 0), score("시장성", 0), score("사업 연계성", 0)),
                0);

        AiEvaluationReportResponse report = service.mapAgentResponse(agent, "PAT-VAL09");

        // 실제 매각 후보(4축 0점)는 합계 0으로 보존되어 '미산출'(null)과 구분된다.
        assertThat(report.totalScore()).isZero();
        assertThat(report.degraded()).isFalse();
    }

    @Test
    void missingScoresYieldNullTotalAndDegraded() {
        AgentEvaluateResponse agent = agentResponse(List.of(), null);

        AiEvaluationReportResponse report = service.mapAgentResponse(agent, "PAT-VAL09");

        // 점수 미산출은 합계 null + degraded=true로 명시 구분된다(0점 매각후보와 혼동 안 됨).
        assertThat(report.totalScore()).isNull();
        assertThat(report.degraded()).isTrue();
    }
}
