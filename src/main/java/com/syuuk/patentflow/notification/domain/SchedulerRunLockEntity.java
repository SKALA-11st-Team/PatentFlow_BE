/**
 * @author 유건욱
 * @date 2026-06-15
 */
package com.syuuk.patentflow.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * @description 멀티 replica 환경에서 일 1회 스케줄 작업이 한 인스턴스에서만 실행되도록 하는 DB 실행 락.
 *     (job_name, run_date)를 그대로 PK로 삼아 동시 INSERT 중 하나만 통과한다(새 의존성 없이 분산 락).
 */
@Entity
@Table(name = "scheduler_run_locks")
public class SchedulerRunLockEntity {

    @Id
    @Column(length = 128)
    private String id; // jobName + ":" + runDate

    @Column(name = "job_name", nullable = false, length = 64)
    private String jobName;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "claimed_at", nullable = false)
    private OffsetDateTime claimedAt;

    protected SchedulerRunLockEntity() {
    }

    public SchedulerRunLockEntity(String jobName, LocalDate runDate) {
        this.jobName = jobName;
        this.runDate = runDate;
        this.id = jobName + ":" + runDate;
        this.claimedAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public String getId() {
        return id;
    }

    public String getJobName() {
        return jobName;
    }

    public LocalDate getRunDate() {
        return runDate;
    }

    public OffsetDateTime getClaimedAt() {
        return claimedAt;
    }
}
