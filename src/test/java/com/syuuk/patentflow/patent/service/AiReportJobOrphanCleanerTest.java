package com.syuuk.patentflow.patent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.syuuk.patentflow.patent.domain.AiReportJobEntity;
import com.syuuk.patentflow.patent.dto.AiReportJobStatus;
import com.syuuk.patentflow.patent.repository.AiReportJobRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class AiReportJobOrphanCleanerTest {

    private final AiReportJobRepository jobRepository = mock(AiReportJobRepository.class);
    private final AiReportJobOrphanCleaner cleaner = new AiReportJobOrphanCleaner(jobRepository);

    @Test
    void marksPendingAndRunningJobsFailedOnBoot() {
        AiReportJobEntity pending = new AiReportJobEntity("JOB-1", "PAT-1", OffsetDateTime.now());
        AiReportJobEntity running = new AiReportJobEntity("JOB-2", "PAT-2", OffsetDateTime.now());
        running.markRunning(OffsetDateTime.now());
        when(jobRepository.findByStatusIn(anyCollection())).thenReturn(List.of(pending, running));

        cleaner.run(null);

        assertThat(pending.getStatus()).isEqualTo(AiReportJobStatus.FAILED);
        assertThat(running.getStatus()).isEqualTo(AiReportJobStatus.FAILED);
        assertThat(pending.getMessage()).contains("재시작");
        assertThat(running.getFinishedAt()).isNotNull();
        verify(jobRepository).saveAll(List.of(pending, running));
    }

    @Test
    void doesNothingWhenNoOrphanJobsExist() {
        when(jobRepository.findByStatusIn(anyCollection())).thenReturn(List.of());

        cleaner.run(null);

        verify(jobRepository, never()).saveAll(ArgumentMatchers.<List<AiReportJobEntity>>any());
    }
}
