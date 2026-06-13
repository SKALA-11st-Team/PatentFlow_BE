package com.syuuk.patentflow.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * BE-14: 접근 거부(403)를 ERROR dispatch 없이 JSON으로 직접 응답한다.
 *
 * 기본 핸들러는 {@code sendError(403)}을 호출하는데, 그러면 /error로의 ERROR dispatch에서
 * 시큐리티 체인이 다시 실행되고 인증이 복원되지 않는 경우
 * {@code HttpStatusEntryPoint(UNAUTHORIZED)}가 응답을 401로 바꿔 버린다.
 * 그 401을 FE가 토큰 만료로 오인해 refresh→재시도(같은 stale CSRF 토큰)를 반복하는 것이
 * CSRF "핑퐁"의 정체다. 여기서 직접 바디를 쓰면 dispatch가 일어나지 않는다.
 *
 * CSRF 거부는 {@code CSRF_TOKEN_INVALID} 코드로 구분해 FE가 토큰 재발급 후 1회 재시도할 수 있게 한다.
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        ErrorCode errorCode = exception instanceof CsrfException
                ? ErrorCode.CSRF_TOKEN_INVALID
                : ErrorCode.ACCESS_DENIED;
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(errorCode));
    }
}
