package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.patent.domain.AiReportJobEntity;
import com.syuuk.patentflow.patent.dto.AiReportJobStatus;
import com.syuuk.patentflow.patent.repository.AiReportJobRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 분기 활성화 시 대상 특허 전체에 대해 AI 레포트를 자동 생성하는 배치 서비스.
 *
 * - @Async("aiReportBatchExecutor") 덕분에 스케줄러 스레드를 블로킹하지 않는다.
 * - 특허 1건당 에이전트 응답까지 최대 20분이 소요될 수 있으므로 순차 처리한다.
 * - 개별 특허 실패가 나머지 처리를 중단시키지 않도록 예외를 잡고 경고 로그만 남긴다.
 * - 생성 완료된 특허의 상태는 PatentReviewService.generateAiReportForBatch() 내부에서
 *   REVIEW_QUARTER_STARTED → MAIL_READY 로 자동 전이된다.
 * - 배치도 on-demand 요청과 동일하게 AiReportJobEntity 를 단일 출처로 생성해 중복 평가를 막는다:
 *   배치가 평가 중인 특허는 활성 잡 row 가 남아 사용자의 'AI 레포트 요청'(requestAiReport) 중복
 *   검사에 걸려 같은 특허를 동시에 평가해 결과를 덮어쓰는 일이 없고, FE 상태 폴링/고아 정리 대상에도
 *   포함된다. 실행 자체는 AiReportJobService.runJob() 을 공유해 상태 전이·알림 로직을 일원화한다.
 */
@Service
public class AiReportBatchService {

    private static final Logger log = LoggerFactory.getLogger(AiReportBatchService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<AiReportJobStatus> ACTIVE_STATUSES =
            List.of(AiReportJobStatus.PENDING, AiReportJobStatus.RUNNING);

    private final AiReportJobService aiReportJobService;
    private final AiReportJobRepository jobRepository;

    public AiReportBatchService(AiReportJobService aiReportJobService, AiReportJobRepository jobRepository) {
        this.aiReportJobService = aiReportJobService;
        this.jobRepository = jobRepository;
    }

    @Async("aiReportBatchExecutor")
    public void generateReportsForQuarter(List<String> patentIds, String quarterKey) {
        log.info("[AiReportBatch] 분기 {} 배치 시작 — 대상 특허 {}건", quarterKey, patentIds.size());
        int success = 0;
        int failed = 0;
        int skipped = 0;

        for (String patentId : patentIds) {
            try {
                String jobId = createJobIfAbsent(patentId);
                if (jobId == null) {
                    // 이미 활성 잡(on-demand 요청 또는 직전 배치)이 같은 특허를 평가 중 — 중복 평가 방지.
                    skipped++;
                    log.info("[AiReportBatch] 활성 잡 존재로 건너뜀: {} ({}/{})",
                            patentId, success + failed + skipped, patentIds.size());
                    continue;
                }
                // runJob 은 동기 실행해 분기 배치의 순차 처리(특허 1건당 최대 20분)를 유지한다.
                aiReportJobService.runJob(jobId);
                success++;
                log.info("[AiReportBatch] 레포트 생성 완료: {} ({}/{})",
                        patentId, success + failed + skipped, patentIds.size());
            } catch (Exception e) {
                failed++;
                log.warn("[AiReportBatch] 레포트 생성 실패: {} — {}", patentId, e.getMessage());
            }
        }

        log.info("[AiReportBatch] 분기 {} 배치 완료 — 성공 {}건 / 실패 {}건 / 건너뜀 {}건",
                quarterKey, success, failed, skipped);
    }

    /**
     * 특허에 활성 잡이 없을 때만 새 잡 row 를 생성해 jobId 를 반환한다(이미 활성 잡이 있으면 null).
     * on-demand 경로(requestAiReport)와 동일하게 saveAndFlush + DataIntegrityViolation 처리로
     * 동시 요청 충돌을 흡수한다.
     */
    private String createJobIfAbsent(String patentId) {
        if (hasActiveJob(patentId)) {
            return null;
        }
        OffsetDateTime now = OffsetDateTime.now(KST);
        try {
            AiReportJobEntity job = jobRepository.saveAndFlush(
                    new AiReportJobEntity(newJobId(patentId, now), patentId, now));
            return job.getJobId();
        } catch (DataIntegrityViolationException duplicate) {
            // 거의 동시에 on-demand 요청과 충돌 — 한쪽만 INSERT 통과. 충돌한 배치 항목은 건너뛴다.
            log.info("[AiReportBatch] concurrent request collapsed for patent {}: {}",
                    patentId, duplicate.getMessage());
            return null;
        }
    }

    private boolean hasActiveJob(String patentId) {
        return jobRepository
                .findFirstByPatentIdAndStatusInOrderByRequestedAtDesc(patentId, ACTIVE_STATUSES)
                .isPresent();
    }

    private String newJobId(String patentId, OffsetDateTime now) {
        return "AIJOB-" + patentId + "-" + now.toInstant().toEpochMilli();
    }
}
