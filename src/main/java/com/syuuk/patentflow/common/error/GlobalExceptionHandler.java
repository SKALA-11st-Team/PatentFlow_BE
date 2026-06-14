package com.syuuk.patentflow.common.error;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 동기 Agent 연동 경로(설정 페이지의 가치평가 기준 프롬프트 조회/저장)에서 AiReportAgentClient가
    // 던지는 업스트림 실패 신호. 해당 클라이언트는 비200/전송실패를 전부 "Agent ..."로 시작하는
    // IllegalStateException으로 래핑하므로, 이 접두사로 업스트림(에이전트) 장애를 일반 내부오류와 구분한다.
    private static final String AGENT_FAILURE_MESSAGE_PREFIX = "Agent ";
    // 업스트림 의존성(AI 평가 서비스) 장애에는 일반 500이 아니라 502 게이트웨이 시맨틱을 사용한다.
    // (드리프트 금지 규칙: LLM/Agent 실패는 가짜 응답으로 가리지 않고 가시적으로 실패해야 한다.)
    private static final String AGENT_UNAVAILABLE_CODE = "AGENT_UNAVAILABLE";
    private static final String AGENT_UNAVAILABLE_MESSAGE = "AI 평가 서비스에 일시적으로 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.";

    @ExceptionHandler(PatentFlowException.class)
    public ResponseEntity<ErrorResponse> handlePatentFlowException(PatentFlowException exception) {
        ErrorCode errorCode = exception.errorCode();
        log.warn("PatentFlowException occurred: {} - {}", errorCode.name(), exception.getMessage());
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        Map<String, Object> details = new HashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> details.put(error.getField(), error.getDefaultMessage()));
        log.warn("Validation failed: {}", details);
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, details));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception exception) {
        log.warn("Malformed request: {}", exception.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException exception) {
        log.warn("Authentication failed: {}", exception.getMessage());
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.status()).body(ErrorResponse.of(ErrorCode.UNAUTHORIZED));
    }

    // 동기 Agent 연동 실패(SettingsController의 가치평가 기준 프롬프트 GET/PUT)는 일반 500이 아니라
    // 업스트림 의존성 장애(502)로 매핑한다. AiReportAgentClient가 "Agent ..."로 시작하는 메시지로
    // 래핑하는 경우에만 적용하고, 그 외 모든 IllegalStateException(JSON 저장/암호화/JWT 등 진짜 내부오류)은
    // 기존 동작대로 일반 500으로 처리해 시맨틱을 보존한다.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException exception) {
        String message = exception.getMessage();
        if (message != null && message.startsWith(AGENT_FAILURE_MESSAGE_PREFIX)) {
            log.warn("Agent 동기 호출 실패(업스트림 장애): {}", message);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ErrorResponse(AGENT_UNAVAILABLE_CODE, AGENT_UNAVAILABLE_MESSAGE,
                            Map.of(), OffsetDateTime.now(ZoneId.of("Asia/Seoul"))));
        }
        return handleException(exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        log.error("Unhandled exception occurred: ", exception);
        return ResponseEntity.internalServerError().body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }
}
