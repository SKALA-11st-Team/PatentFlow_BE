package com.syuuk.patentflow.common.audit;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class SecurityAuditLoggerTest {

    private final SecurityAuditLogger auditLogger = new SecurityAuditLogger();

    @Test
    void recordsEventsWithoutThrowingEvenWithoutRequestContextOrActor() {
        // 요청 컨텍스트 없음(ip/userAgent="-")·actor null 도 안전하게 처리한다.
        assertThatCode(() -> {
            auditLogger.record(SecurityAuditLogger.Event.LOGIN_SUCCESS, "a@x.com");
            auditLogger.record(SecurityAuditLogger.Event.ACCOUNT_LOCKED, "a@x.com", "failures=5");
            auditLogger.record(SecurityAuditLogger.Event.LOGOUT, null);
        }).doesNotThrowAnyException();
    }
}
