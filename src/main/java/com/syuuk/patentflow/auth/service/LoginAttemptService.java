/**
 * @author 유건욱
 * @date 2026-05-20
 */
package com.syuuk.patentflow.auth.service;

import com.syuuk.patentflow.auth.config.AuthProperties;
import com.syuuk.patentflow.common.audit.SecurityAuditLogger;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * 로그인 실패/락아웃 정책(임계치·잠금시간)을 보유하고, 저장은 LoginAttemptStore(인메모리/Redis)에 위임한다.
 * 호출부(AuthService) 시그니처는 불변이며 저장 구현만 patentflow.redis.enabled로 선택된다.
 */
@Service
public class LoginAttemptService {

    private final AuthProperties properties;
    private final LoginAttemptStore store;
    private final SecurityAuditLogger auditLogger;

    public LoginAttemptService(AuthProperties properties, LoginAttemptStore store, SecurityAuditLogger auditLogger) {
        this.properties = properties;
        this.store = store;
        this.auditLogger = auditLogger;
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-COM-01
     * @description 해당 이메일 계정이 잠금 상태이면 로그인 차단 예외를 던진다.
     */
    public void assertNotLocked(String email) {
        if (store.isLocked(key(email))) {
            throw new PatentFlowException(ErrorCode.LOGIN_LOCKED);
        }
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-COM-01
     * @description 로그인 성공 시 누적된 실패 횟수를 초기화한다.
     */
    public void recordSuccess(String email) {
        store.reset(key(email));
    }

    /**
     * @relatedFR FR-COM-01
     * @relatedUI UI-COM-01
     * @description 로그인 실패를 누적하고, 임계치 도달 시 계정을 잠그고 감사 로깅한다.
     */
    public void recordFailure(String email) {
        String key = key(email);
        Duration window = Duration.ofSeconds(properties.getLoginLockSeconds());
        int failures = store.incrementFailures(key, window);
        if (failures >= properties.getMaxLoginFailures()) {
            store.setLock(key, Duration.ofSeconds(properties.getLoginLockSeconds()));
            auditLogger.record(SecurityAuditLogger.Event.ACCOUNT_LOCKED, email,
                    "failures=" + failures);
        }
    }

    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
