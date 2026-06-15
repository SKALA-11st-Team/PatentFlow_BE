package com.syuuk.patentflow.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    // AI 레포트 배치 생성 전용 스레드 풀
    // - 특허 1건당 최대 10분 이상 소요될 수 있으므로 스레드 수를 최소로 제한
    // - corePoolSize=1: 분기 활성화가 겹치는 경우를 대비해 최소 1개 보장
    // - maxPoolSize=2: 병렬 실행 상한을 2로 제한해 에이전트 과부하 방지
    // - queueCapacity=50: 활성화 요청이 몰릴 경우 최대 50개 대기 허용
    // - CallerRunsPolicy: 큐가 꽉 차면 호출 스레드(스케줄러)에서 직접 실행 — 요청 손실 방지
    //   (분기 배치는 @Async 스케줄러 스레드에서만 호출되므로 동기 실행이 안전하다)
    @Bean(name = "aiReportBatchExecutor")
    public Executor aiReportBatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-report-batch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    // 단건 on-demand AI 레포트 생성 전용 스레드 풀 (HTTP 요청에서 트리거)
    // - 분기 배치(aiReportBatchExecutor)와 분리한다: 배치가 1스레드를 수 시간 점유해도
    //   사용자 요청 잡이 굶지 않고, CallerRunsPolicy로 인해 runJob이 HTTP 요청 스레드에서
    //   동기 실행되어 응답이 에이전트 타임아웃(최대 수십 분)까지 지연되는 것을 막는다.
    // - AbortPolicy(기본): 풀+큐가 모두 포화되면 RejectedExecutionException을 던진다.
    //   잡 row는 제출 전 이미 PENDING으로 커밋되어 있으므로 거부되어도 유실되지 않는다
    //   (HTTP 스레드 블로킹 대신 PENDING 유지 — 복구 가능).
    @Bean(name = "aiReportOnDemandExecutor")
    public Executor aiReportOnDemandExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-report-ondemand-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
