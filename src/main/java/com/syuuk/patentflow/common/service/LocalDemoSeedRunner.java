package com.syuuk.patentflow.common.service;

import com.syuuk.patentflow.patent.service.PatentReviewService;
import java.sql.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "demo"})
@Order(0)
public class LocalDemoSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDemoSeedRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final PatentReviewService patentReviewService;

    public LocalDemoSeedRunner(JdbcTemplate jdbcTemplate, PatentReviewService patentReviewService) {
        this.jdbcTemplate = jdbcTemplate;
        this.patentReviewService = patentReviewService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgresDatabase()) {
            log.info("Skipping local/demo SQL seed because the datasource is not PostgreSQL.");
            return;
        }

        if (count("patents") == 0) {
            runScript("db/seed/skax_patents.sql");
        } else {
            log.info("Skipping SK AX patent seed because patents table already has data.");
        }

        if (needsCoreWorkflowSeed()) {
            runScript("db/seed/core_review_workflow_seed.sql");
            patentReviewService.refreshDepartmentCache();
        } else {
            log.info("Skipping core review workflow seed because baseline rows already exist.");
        }

        if (needsDemoWorkflowSeed()) {
            runScript("db/seed/demo_workflow_seed.sql");
            patentReviewService.refreshDepartmentCache();
        } else {
            log.info("Skipping demo workflow seed because demo rows already exist.");
        }
    }

    private boolean isPostgresDatabase() {
        String productName = jdbcTemplate.execute((Connection connection) ->
                connection.getMetaData().getDatabaseProductName());
        return productName != null && productName.toLowerCase().contains("postgresql");
    }

    private boolean needsCoreWorkflowSeed() {
        return count("departments") == 0
                || count("users", "role = 'BUSINESS'") == 0
                || count("system_settings", "setting_key LIKE 'country.extension.%'") < 5
                || count("quarter_settings") == 0
                || count("patent_review_history", "quarter_key = '2026-Q2'") == 0;
    }

    private boolean needsDemoWorkflowSeed() {
        return count("users", "username = 'business'") == 0
                || count("mailing_history", "mailing_id LIKE 'MAIL-DEMO-%'") == 0
                || count("business_submissions", "submission_id LIKE '%-DEMO-SUB-%'") == 0;
    }

    private int count(String tableName) {
        return count(tableName, null);
    }

    private int count(String tableName, String whereClause) {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        if (whereClause != null && !whereClause.isBlank()) {
            sql += " WHERE " + whereClause;
        }
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private void runScript(String path) {
        log.info("Running local/demo seed script: {}", path);
        jdbcTemplate.execute((Connection connection) -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(path));
            return null;
        });
    }
}
