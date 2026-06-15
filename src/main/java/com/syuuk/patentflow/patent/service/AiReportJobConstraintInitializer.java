package com.syuuk.patentflow.patent.service;

import java.sql.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * @relatedFR FR-LEGAL-18
 * @description ddl-auto(update)로는 표현할 수 없는 '활성 상태 부분 유니크 인덱스'를 기동 시 보장한다.
 *     같은 특허에 PENDING/RUNNING 잡이 동시에 둘 생기는 TOCTOU를 DB 레벨에서 차단해
 *     AiReportJobService.requestAiReport의 DataIntegrityViolation 충돌-병합 경로가 실제로 동작하게 한다.
 *     PostgreSQL 전용(부분 인덱스). 다른 DB(H2 테스트 등)에서는 건너뛴다(앱레벨 재조회가 폴백).
 */
@Component
@Order(5)
public class AiReportJobConstraintInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiReportJobConstraintInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public AiReportJobConstraintInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgresDatabase()) {
            return;
        }
        jdbcTemplate.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_report_job_active_patent"
                        + " ON patent_ai_report_jobs (patent_id)"
                        + " WHERE status IN ('PENDING', 'RUNNING')");
        log.info("Ensured active AI report job partial unique index (uq_ai_report_job_active_patent).");
    }

    private boolean isPostgresDatabase() {
        String productName = jdbcTemplate.execute(
                (Connection connection) -> connection.getMetaData().getDatabaseProductName());
        return productName != null && productName.toLowerCase().contains("postgresql");
    }
}
