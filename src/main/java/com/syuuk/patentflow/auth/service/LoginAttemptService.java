package com.syuuk.patentflow.auth.service;

import com.syuuk.patentflow.auth.config.AuthProperties;
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

    public LoginAttemptService(AuthProperties properties, LoginAttemptStore store) {
        this.properties = properties;
        this.store = store;
    }

    public void assertNotLocked(String email) {
        if (store.isLocked(key(email))) {
            throw new PatentFlowException(ErrorCode.LOGIN_LOCKED);
        }
    }

    public void recordSuccess(String email) {
        store.reset(key(email));
    }

    public void recordFailure(String email) {
        String key = key(email);
        Duration window = Duration.ofSeconds(properties.getLoginLockSeconds());
        int failures = store.incrementFailures(key, window);
        if (failures >= properties.getMaxLoginFailures()) {
            store.setLock(key, Duration.ofSeconds(properties.getLoginLockSeconds()));
        }
    }

    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
