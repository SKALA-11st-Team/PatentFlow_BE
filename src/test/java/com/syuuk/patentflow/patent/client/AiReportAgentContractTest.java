package com.syuuk.patentflow.patent.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.syuuk.patentflow.patent.client.AiReportAgentClient.AgentEvaluateResponse;
import org.junit.jupiter.api.Test;

/**
 * CONTRACT-10(최소 1단계): BE↔Agent 평가 응답 계약 스냅샷.
 *
 * 에이전트(app/api.py PatentEvaluationResponse)가 실제로 내보내는 camelCase JSON을 BE record로
 * 역직렬화해, ORCH-06/AIREPORT-02에서 추가한 리치 근거 필드가 @JsonIgnoreProperties로 조용히
 * 드롭되지 않는지(=필드명 정합) 검증한다. 필드명이 어긋나면 이 테스트가 깨져 런타임 무음 유실을 막는다.
 */
class AiReportAgentContractTest {

    // 에이전트 PatentEvaluationResponse의 실제 출력 형태(camelCase). 리치 근거 필드를 모두 포함한다.
    private static final String AGENT_RESPONSE_JSON = """
            {
              "patentId": "PAT-CONTRACT",
              "scores": [
                {
                  "category": "시장성",
                  "score": 70,
                  "grade": "B",
                  "evidence": "시장성 근거",
                  "evidenceDetails": [
                    {"text": "AI 반도체 시장 급성장", "source": {"title": "한국경제", "url": "https://example.com/1"}},
                    {"text": "반도체 산업 전망", "source": {"title": "KIET", "url": null}}
                  ]
                }
              ],
              "recommendation": "유지 권고",
              "summaryMarkdown": "# 요약",
              "valuationReportMarkdown": "# 보고서",
              "artifactDir": "/tmp/run",
              "totalScore": 300,
              "averageScore": 75.0,
              "finalGrade": "B",
              "degraded": false,
              "failureReason": null,
              "warnings": ["w1"],
              "evidenceConfidence": "high",
              "missingInformation": ["상용화 매출 실적"],
              "keyEvidence": "기술성: 기술성 근거",
              "judgementGrounds": ["합산 300/400, 평균 75/100, 등급 B."],
              "businessCheckRequests": ["사업부 적용 계획 확인"],
              "externalSources": [
                {"title": "한국경제", "url": "https://example.com/1"},
                {"title": "KIET", "url": null}
              ],
              "summaryBrief": {"one_line_summary": "한 줄 요약", "core_idea": "핵심 아이디어"},
              "reportSections": {
                "evaluationScope": "평가 범위 본문",
                "judgmentBasis": "판단 근거 본문",
                "axisDetails": "축별 상세 본문",
                "roleChecklist": "역할별 확인 본문",
                "finalOpinion": "최종 의견 본문"
              },
              "generatedAt": "2026-06-09T00:00:00Z"
            }
            """;

    private final ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @Test
    void deserializesAllRichEvidenceFieldsWithoutSilentDrop() throws Exception {
        AgentEvaluateResponse response = objectMapper.readValue(AGENT_RESPONSE_JSON, AgentEvaluateResponse.class);

        // 스칼라 계약
        assertThat(response.patentId()).isEqualTo("PAT-CONTRACT");
        assertThat(response.totalScore()).isEqualTo(300);
        assertThat(response.averageScore()).isEqualTo(75.0);
        assertThat(response.finalGrade()).isEqualTo("B");
        assertThat(response.reportMarkdown()).isEqualTo("# 보고서");

        // ORCH-06/AIREPORT-02: 리포트 레벨 리치 근거가 드롭되지 않는다.
        assertThat(response.missingInformation()).containsExactly("상용화 매출 실적");
        assertThat(response.keyEvidence()).isEqualTo("기술성: 기술성 근거");
        assertThat(response.judgementGrounds()).containsExactly("합산 300/400, 평균 75/100, 등급 B.");
        assertThat(response.businessCheckRequests()).containsExactly("사업부 적용 계획 확인");
        assertThat(response.externalSources()).hasSize(2);
        assertThat(response.externalSources().get(0).title()).isEqualTo("한국경제");
        assertThat(response.externalSources().get(0).url()).isEqualTo("https://example.com/1");
        assertThat(response.externalSources().get(1).url()).isNull();

        // 계약 신호: warnings·evidenceConfidence가 @JsonIgnoreProperties로 드롭되지 않는다.
        assertThat(response.warnings()).containsExactly("w1");
        assertThat(response.evidenceConfidence()).isEqualTo("high");

        // 구조화 렌더링 필드(summaryBrief·reportSections)가 드롭되지 않는다.
        assertThat(response.summaryBrief()).containsEntry("one_line_summary", "한 줄 요약");
        assertThat(response.reportSections())
                .containsEntry("evaluationScope", "평가 범위 본문")
                .containsEntry("finalOpinion", "최종 의견 본문");

        // 축별 evidenceDetails(클릭형 출처)가 드롭되지 않는다.
        assertThat(response.scores()).hasSize(1);
        AiReportAgentClient.AgentScoreItem market = response.scores().get(0);
        assertThat(market.evidenceDetails()).hasSize(2);
        assertThat(market.evidenceDetails().get(0).text()).isEqualTo("AI 반도체 시장 급성장");
        assertThat(market.evidenceDetails().get(0).source().title()).isEqualTo("한국경제");
        assertThat(market.evidenceDetails().get(0).source().url()).isEqualTo("https://example.com/1");
        assertThat(market.evidenceDetails().get(1).source().url()).isNull();
    }
}
