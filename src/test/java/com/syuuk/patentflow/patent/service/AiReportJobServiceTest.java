package com.syuuk.patentflow.patent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.patent.client.AiReportAgentClient;
import com.syuuk.patentflow.patent.domain.AiReportJobEntity;
import com.syuuk.patentflow.patent.repository.AiReportJobRepository;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * @relatedFR FR-LEGAL-18
 * @description AI 레포트 재생성 권한(서버 강제) 회귀 테스트. FE 버튼 노출 판단뿐 아니라
 *     requestAiReport()가 BUSINESS 행위자에게 설정값을 강제하는지 검증한다.
 */
class AiReportJobServiceTest {

    private final AiReportJobRepository jobRepository = mock(AiReportJobRepository.class);
    private final PatentReviewService patentReviewService = mock(PatentReviewService.class);
    private final PatentWorkflowService workflowService = mock(PatentWorkflowService.class);
    private final Executor aiReportBatchExecutor = mock(Executor.class);
    private final Executor aiReportOnDemandExecutor = mock(Executor.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AiReportAgentClient agentClient = mock(AiReportAgentClient.class);
    private final SystemSettingsService systemSettingsService = mock(SystemSettingsService.class);

    private final AiReportJobService service = new AiReportJobService(
            jobRepository,
            patentReviewService,
            workflowService,
            aiReportBatchExecutor,
            aiReportOnDemandExecutor,
            eventPublisher,
            agentClient,
            systemSettingsService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void businessActorIsRejectedWhenRegenSettingDisabled() {
        authenticateAs("ROLE_BUSINESS");
        when(systemSettingsService.getAiReportRegenBusinessAllowed()).thenReturn(false);

        assertThatThrownBy(() -> service.requestAiReport("PAT-2026-0001"))
                .isInstanceOf(PatentFlowException.class)
                .extracting(ex -> ((PatentFlowException) ex).errorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        // 권한 거부는 특허 조회/잡 생성/에이전트 호출 이전에 차단되어야 한다.
        verify(patentReviewService, never()).findPatent(any());
        verify(jobRepository, never()).saveAndFlush(any(AiReportJobEntity.class));
        verify(aiReportBatchExecutor, never()).execute(any());
    }

    @Test
    void businessActorPassesGateWhenRegenSettingEnabled() {
        authenticateAs("ROLE_BUSINESS");
        when(systemSettingsService.getAiReportRegenBusinessAllowed()).thenReturn(true);
        // 게이트 통과 이후 워크플로우 상태 가드까지 진입함을 특허 조회 호출로 확인한다.
        when(patentReviewService.findPatent("PAT-2026-0001"))
                .thenThrow(new PatentFlowException(ErrorCode.PATENT_NOT_FOUND));

        assertThatThrownBy(() -> service.requestAiReport("PAT-2026-0001"))
                .isInstanceOf(PatentFlowException.class)
                .extracting(ex -> ((PatentFlowException) ex).errorCode())
                .isEqualTo(ErrorCode.PATENT_NOT_FOUND);

        verify(patentReviewService).findPatent("PAT-2026-0001");
    }

    @Test
    void nonBusinessActorIsNeverGatedByRegenSetting() {
        authenticateAs("ROLE_LEGAL");
        when(patentReviewService.findPatent("PAT-2026-0001"))
                .thenThrow(new PatentFlowException(ErrorCode.PATENT_NOT_FOUND));

        assertThatThrownBy(() -> service.requestAiReport("PAT-2026-0001"))
                .isInstanceOf(PatentFlowException.class)
                .extracting(ex -> ((PatentFlowException) ex).errorCode())
                .isEqualTo(ErrorCode.PATENT_NOT_FOUND);

        // LEGAL/ADMIN은 설정을 조회조차 하지 않는다(설정과 무관하게 항상 허용).
        verify(systemSettingsService, never()).getAiReportRegenBusinessAllowed();
        verify(patentReviewService).findPatent("PAT-2026-0001");
    }

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "user", "n/a", List.of(new SimpleGrantedAuthority(role))));
    }
}
