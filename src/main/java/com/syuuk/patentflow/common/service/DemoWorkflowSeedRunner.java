package com.syuuk.patentflow.common.service;

import java.sql.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

/**
 * 로컬/데모 프로필 전용. QuarterActivationScheduler(@Order 10)가 patent_review_history 행을
 * 생성한 뒤(@Order 20) demo_workflow_seed.sql로 발표용 워크플로 상태를 적용한다.
 *
 * 멱등 조건: PAT-2026-0097 이 아직 REVIEW_QUARTER_STARTED 상태일 때만 실행한다.
 * 이미 데모 상태가 적용됐거나 담당자가 수동 진행한 경우에는 덮어쓰지 않는다.
 */
@Component
@Profile({"local", "demo"})
@Order(20)
public class DemoWorkflowSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoWorkflowSeedRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public DemoWorkflowSeedRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgresDatabase()) {
            return;
        }

        Integer q2HistoryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patent_review_history WHERE quarter_key = '2026-Q2'",
                Integer.class);
        if (q2HistoryCount == null || q2HistoryCount == 0) {
            // 데모 시드는 발표 분기(2026-Q2)에 고정돼 있다. 분기가 넘어가면 매칭 행이 없어
            // 시드가 적용되지 않으므로, 조용한 no-op 대신 WARN으로 운영자가 인지하게 한다.
            log.warn(
                    "Skipping demo workflow seed: no patent_review_history rows for hard-coded quarter '2026-Q2'."
                            + " If the demo quarter has rolled over, update DemoWorkflowSeedRunner and demo_workflow_seed.sql.");
            return;
        }

        Integer alreadyApplied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patent_review_history"
                        + " WHERE patent_id = 'PAT-2026-0097' AND quarter_key = '2026-Q2'"
                        + " AND review_workflow_status != 'REVIEW_QUARTER_STARTED'",
                Integer.class);
        if (alreadyApplied != null && alreadyApplied > 0) {
            log.info("Skipping demo workflow seed: demo states already applied.");
            return;
        }

        log.info("Applying demo workflow seed...");
        jdbcTemplate.execute((Connection connection) -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/seed/demo_workflow_seed.sql"));
            return null;
        });
        log.info("Demo workflow seed applied.");
    }

    private boolean isPostgresDatabase() {
        String productName = jdbcTemplate.execute(
                (Connection connection) -> connection.getMetaData().getDatabaseProductName());
        return productName != null && productName.toLowerCase().contains("postgresql");
    }
}
