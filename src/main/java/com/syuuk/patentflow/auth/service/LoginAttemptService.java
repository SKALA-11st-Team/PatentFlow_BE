package com.syuuk.patentflow.auth.service;

import com.syuuk.patentflow.auth.config.AuthProperties;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private final AuthProperties properties;
    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(AuthProperties properties) {
        this.properties = properties;
    }

    public void assertNotLocked(String email) {
        AttemptState state = attempts.get(key(email));
        if (state == null || state.lockedUntil == null) {
            return;
        }
        if (state.lockedUntil.isAfter(Instant.now())) {
            throw new PatentFlowException(ErrorCode.LOGIN_LOCKED);
        }
        attempts.remove(key(email));
    }

    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    public void recordFailure(String email) {
        String key = key(email);
        attempts.compute(key, (ignored, current) -> {
            AttemptState next = current == null ? new AttemptState() : current;
            next.failures += 1;
            if (next.failures >= properties.getMaxLoginFailures()) {
                next.lockedUntil = Instant.now().plusSeconds(properties.getLoginLockSeconds());
            }
            return next;
        });
    }

    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static class AttemptState {
        private int failures;
        private Instant lockedUntil;
    }
}
