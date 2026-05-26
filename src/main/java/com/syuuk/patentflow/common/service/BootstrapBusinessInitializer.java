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

@Component
@Order(1)
public class BootstrapBusinessInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapBusinessInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String departmentId;
    private final String displayName;

    public BootstrapBusinessInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${patentflow.bootstrap.business.username:}") String username,
            @Value("${patentflow.bootstrap.business.password:}") String password,
            @Value("${patentflow.bootstrap.business.department-id:DEPT-ICT}") String departmentId,
            @Value("${patentflow.bootstrap.business.display-name:사업부 데모 담당자}") String displayName
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.departmentId = departmentId;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isBlank(username) || isBlank(password)) {
            log.info("Bootstrap business user is not configured; skipping initial business user creation.");
            return;
        }

        String normalizedUsername = username.trim();
        UserEntity user = userRepository.findByUsername(normalizedUsername)
                .orElseGet(() -> new UserEntity(
                        "USER-business-demo",
                        normalizedUsername,
                        passwordEncoder.encode(password),
                        "BUSINESS",
                        normalizedDepartmentId(),
                        normalizedDisplayName(normalizedUsername)));

        user.setPassword(passwordEncoder.encode(password));
        user.setRole("BUSINESS");
        user.setDepartmentId(normalizedDepartmentId());
        user.setDisplayName(normalizedDisplayName(normalizedUsername));
        userRepository.save(user);
        log.info("Bootstrap business user upserted: {}", normalizedUsername);
    }

    private String normalizedDepartmentId() {
        return isBlank(departmentId) ? "DEPT-ICT" : departmentId.trim();
    }

    private String normalizedDisplayName(String fallback) {
        return isBlank(displayName) ? fallback : displayName.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
