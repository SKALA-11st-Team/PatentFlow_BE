package com.syuuk.patentflow.common.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AUTH-09: 보안 이벤트 감사 로그. 로그인 성공/실패·계정 락아웃·비밀번호 변경·로그아웃·권한 변경 등을
 * 전용 로거(SECURITY_AUDIT)로 구조화 출력해 사고 대응·추적성을 확보한다(저장은 로그 파이프라인/적재기가 담당).
 */
@Component
public class SecurityAuditLogger {

    private static final Logger AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");

    public enum Event {
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        ACCOUNT_LOCKED,
        PASSWORD_CHANGED,
        LOGOUT,
        ROLE_CHANGED,
        TOKEN_REVOKED
    }

    public void record(Event event, String actor) {
        record(event, actor, null);
    }

    public void record(Event event, String actor, String detail) {
        AUDIT.info("event={} actor={} detail={} ip={} userAgent={}",
                event, nullSafe(actor), nullSafe(detail), clientIp(), userAgent());
    }

    private static String nullSafe(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "-";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return nullSafe(request.getRemoteAddr());
    }

    private static String userAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? "-" : nullSafe(request.getHeader("User-Agent"));
    }
}
