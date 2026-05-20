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

@Component
@Profile("!local & !demo")
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String displayName;

    public BootstrapAdminInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${patentflow.bootstrap.admin.username:}") String username,
            @Value("${patentflow.bootstrap.admin.password:}") String password,
            @Value("${patentflow.bootstrap.admin.display-name:특허관리자}") String displayName
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isBlank(username) || isBlank(password)) {
            log.info("Bootstrap admin is not configured; skipping initial admin creation.");
            return;
        }
        if (userRepository.existsByUsername(username)) {
            log.info("Bootstrap admin already exists: {}", username);
            return;
        }
        userRepository.save(new UserEntity(
                "USER-admin",
                username.trim(),
                passwordEncoder.encode(password),
                "ADMIN",
                null,
                isBlank(displayName) ? username.trim() : displayName.trim()));
        log.info("Bootstrap admin created: {}", username);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
