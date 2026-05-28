package com.syuuk.patentflow.common.service;

import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// local·demo 프로파일에서는 실행하지 않음 — 각 환경의 전용 initializer 또는 마이그레이션으로 처리
@Component
@Profile("!local & !demo")
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

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
        if (userRepository.existsByEmail(email)) {
            log.info("Bootstrap admin already exists: {}", email);
            return;
        }
        userRepository.save(new UserEntity(
                "USER-admin",
                email.trim(),
                passwordEncoder.encode(password),
                "ADMIN",
                null,
                isBlank(displayName) ? email.trim() : displayName.trim()));
        log.info("Bootstrap admin created: {}", email);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
