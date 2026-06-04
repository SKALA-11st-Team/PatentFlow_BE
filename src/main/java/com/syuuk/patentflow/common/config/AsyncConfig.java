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
}
