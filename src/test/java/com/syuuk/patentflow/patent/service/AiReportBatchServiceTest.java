package com.syuuk.patentflow.patent.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.syuuk.patentflow.patent.domain.AiReportJobEntity;
import com.syuuk.patentflow.patent.repository.AiReportJobRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * @description 분기 배치가 on-demand 요청과 동일하게 AiReportJobEntity 를 단일 출처로 사용해
 *     중복 평가를 막는지 검증한다(be-ai-report-5 회귀 테스트). 활성 잡이 있으면 건너뛰고,
 *     없으면 잡 row 를 생성한 뒤 공유 runJob() 으로 실행한다.
 */
class AiReportBatchServiceTest {

    private final AiReportJobService aiReportJobService = mock(AiReportJobService.class);
    private final AiReportJobRepository jobRepository = mock(AiReportJobRepository.class);
    private final AiReportBatchService service = new AiReportBatchService(aiReportJobService, jobRepository);

    @Test
    void createsTrackedJobAndRunsSharedRunJobPerPatent() {
        when(jobRepository.findFirstByPatentIdAndStatusInOrderByRequestedAtDesc(anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(jobRepository.saveAndFlush(any(AiReportJobEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.generateReportsForQuarter(List.of("PAT-2026-0001", "PAT-2026-0002"), "2026-Q3");

        // 각 특허마다 잡 row 가 생성되어 on-demand 중복 검사가 보는 단일 출처가 된다.
        verify(jobRepository, org.mockito.Mockito.times(2)).saveAndFlush(any(AiReportJobEntity.class));
        // 실행은 공유 runJob 으로 위임된다(상태 전이·알림 로직 일원화).
        verify(aiReportJobService, org.mockito.Mockito.times(2)).runJob(anyString());
    }

    @Test
    void skipsPatentWhenActiveJobAlreadyExists() {
        AiReportJobEntity active = new AiReportJobEntity("AIJOB-PAT-2026-0001-1", "PAT-2026-0001", null);
        when(jobRepository.findFirstByPatentIdAndStatusInOrderByRequestedAtDesc(eq("PAT-2026-0001"), anyCollection()))
                .thenReturn(Optional.of(active));

        service.generateReportsForQuarter(List.of("PAT-2026-0001"), "2026-Q3");

        // 활성 잡이 있으면 배치는 새 잡을 만들지도, 평가를 시작하지도 않는다(중복 평가 방지).
        verify(jobRepository, never()).saveAndFlush(any(AiReportJobEntity.class));
        verify(aiReportJobService, never()).runJob(anyString());
    }

    @Test
    void continuesRemainingPatentsWhenOneRunJobFails() {
        when(jobRepository.findFirstByPatentIdAndStatusInOrderByRequestedAtDesc(anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(jobRepository.saveAndFlush(any(AiReportJobEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("agent down"))
                .when(aiReportJobService).runJob(org.mockito.ArgumentMatchers.contains("PAT-2026-0001"));

        service.generateReportsForQuarter(List.of("PAT-2026-0001", "PAT-2026-0002"), "2026-Q3");

        // 개별 실패가 나머지 처리를 중단시키지 않는다.
        verify(aiReportJobService).runJob(org.mockito.ArgumentMatchers.contains("PAT-2026-0002"));
    }
}
