/**
 * @author 유건욱
 * @date 2026-06-15
 */
package com.syuuk.patentflow.notification.service;

import com.syuuk.patentflow.notification.domain.SchedulerRunLockEntity;
import com.syuuk.patentflow.notification.repository.SchedulerRunLockRepository;
import java.time.LocalDate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @relatedFR N/A
 * @relatedUI TODO-UI-ID
 * @description (job_name, run_date) PK INSERT로 일 1회 스케줄을 단일 인스턴스에만 허용한다(분산 락).
 *     REQUIRES_NEW로 독립 트랜잭션에서 시도해, 충돌(DataIntegrityViolation)이 호출자 트랜잭션을 오염시키지 않게 한다.
 *     특정 사용자 기능/화면이 아닌 분기 배치·메일 스케줄러 인프라이므로 FR은 N/A.
 */
@Service
public class SchedulerLockService {

    private final SchedulerRunLockRepository repository;

    public SchedulerLockService(SchedulerRunLockRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaim(String jobName, LocalDate runDate) {
        try {
            repository.saveAndFlush(new SchedulerRunLockEntity(jobName, runDate));
            return true;
        } catch (DataIntegrityViolationException alreadyClaimed) {
            // 다른 replica가 같은 (job, date)를 먼저 선점 — 이 인스턴스는 실행을 건너뛴다.
            return false;
        }
    }
}
