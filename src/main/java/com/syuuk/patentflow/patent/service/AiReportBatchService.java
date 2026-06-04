package com.syuuk.patentflow.patent.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 */
@Service
public class AiReportBatchService {

    private static final Logger log = LoggerFactory.getLogger(AiReportBatchService.class);

    private final PatentReviewService patentReviewService;

    public AiReportBatchService(PatentReviewService patentReviewService) {
        this.patentReviewService = patentReviewService;
    }

    @Async("aiReportBatchExecutor")
    public void generateReportsForQuarter(List<String> patentIds, String quarterKey) {
        log.info("[AiReportBatch] 분기 {} 배치 시작 — 대상 특허 {}건", quarterKey, patentIds.size());
        int success = 0;
        int failed = 0;

        for (String patentId : patentIds) {
            try {
                patentReviewService.generateAiReportForBatch(patentId);
                success++;
                log.info("[AiReportBatch] 레포트 생성 완료: {} ({}/{})", patentId, success + failed, patentIds.size());
            } catch (Exception e) {
                failed++;
                log.warn("[AiReportBatch] 레포트 생성 실패: {} — {}", patentId, e.getMessage());
            }
        }

        log.info("[AiReportBatch] 분기 {} 배치 완료 — 성공 {}건 / 실패 {}건", quarterKey, success, failed);
    }
}
