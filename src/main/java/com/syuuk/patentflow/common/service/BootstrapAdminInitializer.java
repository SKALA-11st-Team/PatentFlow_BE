package com.syuuk.patentflow.common.service;

import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// 모든 프로파일(local/demo 포함)에서 실행 — demo/prod를 구분하지 않으므로 관리자 계정도
// 환경변수(PATENTFLOW_BOOTSTRAP_ADMIN_*) 기준으로 매 기동 시 upsert 한다. (BootstrapBusinessInitializer와 동일 패턴)
@Component
@Order(1)
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    // 데모 시드(core_review_workflow_seed.sql)의 'USER-admin'과 PK 충돌을 피하기 위한 별도 ID
    private static final String BOOTSTRAP_ADMIN_ID = "USER-admin-bootstrap";
    private static final String SEED_ADMIN_ID = "USER-admin";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_BUSINESS = "BUSINESS";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String displayName;

    public BootstrapAdminInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${patentflow.bootstrap.admin.email:}") String email,
            @Value("${patentflow.bootstrap.admin.password:}") String password,
            @Value("${patentflow.bootstrap.admin.display-name:특허관리자}") String displayName
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isBlank(email) || isBlank(password)) {
            log.info("Bootstrap admin is not configured; skipping initial admin creation.");
            return;
        }

        String normalizedEmail = email.trim();
        try {
            // upsert — 기존 ADMIN이 있으면 그 계정을 env 관리자 계정으로 인계해 ADMIN 단일성 정책을 유지한다.
            UserEntity user = userRepository.findByEmail(normalizedEmail)
                    .or(() -> userRepository.findFirstByRoleOrderByCreatedAtAsc(ROLE_ADMIN))
                    .orElseGet(() -> new UserEntity(
                            BOOTSTRAP_ADMIN_ID,
                            normalizedEmail,
                            passwordEncoder.encode(password),
                            ROLE_ADMIN,
                            null,
                            normalizedDisplayName(normalizedEmail)));

            // 승격 추적: 기존 계정을 인계하는 경우 이전 role/departmentId를 남긴다.
            // 시크릿 오설정으로 사업부/법무 계정 이메일이 admin 이메일로 잘못 들어오면
            // 부서 소속이 무경고로 사라지므로, ADMIN이 아니던 계정의 승격은 WARN으로 경고한다.
            String priorRole = user.getRole();
            String priorDepartmentId = user.getDepartmentId();

            user.setEmail(normalizedEmail);
            user.setPassword(passwordEncoder.encode(password));
            user.setRole(ROLE_ADMIN);
            user.setDepartmentId(null);
            user.setUsername(normalizedDisplayName(normalizedEmail));
            userRepository.save(user);
            demoteBootstrapAdminDuplicates(user.getId());
            if (priorRole != null && !ROLE_ADMIN.equals(priorRole)) {
                log.warn("Bootstrap admin promoted existing non-admin account: {} (prior role={}, prior departmentId={}). "
                        + "Department association cleared. Verify PATENTFLOW_BOOTSTRAP_ADMIN_EMAIL is not a business/legal account email.",
                        normalizedEmail, priorRole, priorDepartmentId);
            } else {
                log.info("Bootstrap admin upserted: {}", normalizedEmail);
            }
        } catch (Exception exception) {
            // 부트스트랩 실패가 애플리케이션 기동을 막지 않도록 한다(예: 스키마 마이그레이션 미적용 상황).
            log.error("Bootstrap admin upsert failed; continuing startup. Check the users table schema (email column).", exception);
        }
    }

    private String normalizedDisplayName(String fallback) {
        return isBlank(displayName) ? fallback : displayName.trim();
    }

    private void demoteBootstrapAdminDuplicates(String retainedAdminId) {
        userRepository.findByRoleAndIdNot(ROLE_ADMIN, retainedAdminId).stream()
                .filter(user -> SEED_ADMIN_ID.equals(user.getId()) || BOOTSTRAP_ADMIN_ID.equals(user.getId()))
                .forEach(user -> {
                    user.setRole(ROLE_BUSINESS);
                    user.setDepartmentId(null);
                    userRepository.save(user);
                    log.info("Bootstrap duplicate admin demoted: {}", user.getEmail());
                });
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
