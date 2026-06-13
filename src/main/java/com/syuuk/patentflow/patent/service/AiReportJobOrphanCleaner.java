package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.patent.domain.AiReportJobEntity;
import com.syuuk.patentflow.patent.dto.AiReportJobStatus;
import com.syuuk.patentflow.patent.repository.AiReportJobRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * BE-01: 기동 시 고아 AI 레포트 잡 정리.
 *
 * 잡 실행 상태는 인메모리 비동기 executor에만 있으므로, 재시작 시 PENDING/RUNNING 잡은
 * 영원히 그 상태로 남아 FE 폴링이 끝나지 않고 재요청도 막힌다(ACTIVE_STATUSES 단락).
 * 기동 직후 FAILED로 전환해 사용자가 즉시 재요청할 수 있게 한다.
 *
 * 캐비앗: 멀티 레플리카에서는 다른 파드가 실제 실행 중인 잡도 FAILED 처리될 수 있다.
 * 롤링 배포는 어차피 전 파드를 재시작하고, 잘못 정리돼도 재요청 한 번으로 복구된다.
 */
@Component
@Order(5) // 시드(0) 이후, 분기 활성화(10) 이전
public class AiReportJobOrphanCleaner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiReportJobOrphanCleaner.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AiReportJobRepository jobRepository;

    public AiReportJobOrphanCleaner(AiReportJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<AiReportJobEntity> orphans = jobRepository.findByStatusIn(
                List.of(AiReportJobStatus.PENDING, AiReportJobStatus.RUNNING));
        if (orphans.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(KST);
        orphans.forEach(job -> job.markFailed(now,
                "서버 재시작으로 AI 레포트 생성이 중단되었습니다. 다시 요청해 주세요."));
        jobRepository.saveAll(orphans);
        log.info("[AiReportJob] 고아 잡 {}건을 FAILED로 정리했습니다.", orphans.size());
    }
}
