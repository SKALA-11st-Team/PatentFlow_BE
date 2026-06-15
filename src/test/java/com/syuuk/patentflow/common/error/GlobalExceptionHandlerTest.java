package com.syuuk.patentflow.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * be-common-infra-3: 동기 Agent 연동 실패(SettingsController 가치평가 기준 프롬프트 GET/PUT)는
 * AiReportAgentClient가 "Agent ..."로 시작하는 IllegalStateException으로 래핑한다. 이 경우 일반
 * 500이 아니라 업스트림 의존성 장애(502 BAD_GATEWAY)로 매핑되어야 하고, 그 외 IllegalStateException
 * (JSON 저장/암호화/JWT 등 진짜 내부오류)은 기존 500 시맨틱을 유지해야 한다.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void agentPrefixedIllegalStateMapsToBadGateway() {
        IllegalStateException exception =
                new IllegalStateException("Agent 가치평가 기준 md 목록을 불러오지 못했습니다: connection refused");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalStateException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("AGENT_UNAVAILABLE");
        // 일반 내부오류 문구가 아니라 AI 평가 서비스 연결 실패가 사용자에게 노출되어야 한다.
        assertThat(body.message()).contains("AI 평가 서비스");
        assertThat(body.message()).isNotEqualTo(ErrorCode.INTERNAL_ERROR.message());
    }

    @Test
    void agentSavePrefixedIllegalStateMapsToBadGateway() {
        IllegalStateException exception =
                new IllegalStateException("Agent 가치평가 기준 md를 저장하지 못했습니다: status=503");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalStateException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("AGENT_UNAVAILABLE");
    }

    @Test
    void nonAgentIllegalStateFallsBackToInternalError() {
        // JSON 저장/암호화 등 진짜 내부오류는 기존대로 일반 500을 유지한다.
        IllegalStateException exception =
                new IllegalStateException("AI 평가 레포트 JSON을 저장할 수 없습니다.");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalStateException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INTERNAL_ERROR.name());
        assertThat(response.getBody().message()).isEqualTo(ErrorCode.INTERNAL_ERROR.message());
    }

    @Test
    void nullMessageIllegalStateFallsBackToInternalError() {
        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalStateException(new IllegalStateException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INTERNAL_ERROR.name());
    }
}
