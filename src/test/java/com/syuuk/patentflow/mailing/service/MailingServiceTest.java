package com.syuuk.patentflow.mailing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.response.PageResponse;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.mailing.domain.DepartmentEntity;
import com.syuuk.patentflow.mailing.domain.MailingHistoryEntity;
import com.syuuk.patentflow.mailing.dto.BusinessReviewMailPatentSummary;
import com.syuuk.patentflow.mailing.dto.BusinessReviewMailSendDraft;
import com.syuuk.patentflow.mailing.dto.DepartmentRecipientMappingResponse;
import com.syuuk.patentflow.mailing.dto.MailingHistoryItemResponse;
import com.syuuk.patentflow.mailing.dto.MailingSendRequest;
import com.syuuk.patentflow.mailing.dto.MailingSendResponse;
import com.syuuk.patentflow.mailing.repository.DepartmentRepository;
import com.syuuk.patentflow.mailing.repository.MailingHistoryRepository;
import com.syuuk.patentflow.patent.service.PatentReviewService;
import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class MailingServiceTest {

    @Mock
    private MailingHistoryRepository mailingHistoryRepository;
    @Mock
    private PatentReviewService patentReviewService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SystemSettingsService systemSettingsService;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private MailOAuth2Service mailOAuth2Service;

    private TestMailingService mailingService;

    @BeforeEach
    void setUp() {
        mailingService = new TestMailingService(
                mailingHistoryRepository,
                patentReviewService,
                new ObjectMapper(),
                userRepository,
                systemSettingsService,
                departmentRepository,
                mailOAuth2Service);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordedMailDoesNotAdvanceWorkflowAndUsesCollisionFreeIds() {
        when(mailOAuth2Service.isConnected()).thenReturn(false);
        when(userRepository.findAll()).thenReturn(List.of(
                user("USER-1", "FB@example.com", "DEPT-1"),
                user("USER-2", "Ea@example.com", "DEPT-2")));
        when(patentReviewService.markMailingSent(List.of()))
                .thenReturn(new PatentReviewService.WorkflowBatchUpdateResult(List.of(), List.of()));

        MailingSendResponse response = mailingService.send(new MailingSendRequest(List.of(
                draft("FB@example.com", "PAT-1"),
                draft("Ea@example.com", "PAT-2"))));

        ArgumentCaptor<MailingHistoryEntity> captor = ArgumentCaptor.forClass(MailingHistoryEntity.class);
        verify(mailingHistoryRepository, times(2)).save(captor.capture());
        verify(patentReviewService).markMailingSent(List.of());
        assertThat(response.recordedCount()).isEqualTo(2);
        assertThat(response.updatedCount()).isZero();
        assertThat(captor.getAllValues())
                .extracting(MailingHistoryEntity::getStatus)
                .containsExactly(com.syuuk.patentflow.mailing.domain.MailingStatus.RECORDED,
                        com.syuuk.patentflow.mailing.domain.MailingStatus.RECORDED);
        assertThat(captor.getAllValues())
                .extracting(MailingHistoryEntity::getMailingId)
                .doesNotHaveDuplicates();
    }

    // I1: 건별 처리 — 한 수신자의 실패가 나머지 발송을 막지 않고, 실패 건은 FAILED 이력으로 남는다.
    @Test
    void recipientFailureIsIsolatedAndRecordedAsFailed() {
        when(mailOAuth2Service.isConnected()).thenReturn(true);
        when(mailOAuth2Service.getValidAccessToken()).thenReturn("access-token");
        when(systemSettingsService.getGmailOAuth2ConnectedEmail()).thenReturn("sender@example.com");
        when(userRepository.findAll()).thenReturn(List.of(
                user("USER-1", "success@example.com", "DEPT-1"),
                user("USER-2", "fail@example.com", "DEPT-2")));
        when(patentReviewService.markMailingSent(List.of("PAT-SUCCESS")))
                .thenReturn(new PatentReviewService.WorkflowBatchUpdateResult(List.of("PAT-SUCCESS"), List.of()));
        mailingService.failRecipients = Set.of("fail@example.com");

        MailingSendResponse response = mailingService.send(new MailingSendRequest(List.of(
                draft("success@example.com", "PAT-SUCCESS"),
                draft("fail@example.com", "PAT-FAIL"))));

        assertThat(response.sentCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(1);
        ArgumentCaptor<MailingHistoryEntity> captor = ArgumentCaptor.forClass(MailingHistoryEntity.class);
        verify(mailingHistoryRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(MailingHistoryEntity::getStatus)
                .containsExactly(com.syuuk.patentflow.mailing.domain.MailingStatus.SENT,
                        com.syuuk.patentflow.mailing.domain.MailingStatus.FAILED);
        // 성공 건만 워크플로우 전이 대상이다.
        verify(patentReviewService).markMailingSent(List.of("PAT-SUCCESS"));
    }

    @Test
    void sentByUsesSecurityContextName() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@syuuk.test", "n/a"));
        when(mailOAuth2Service.isConnected()).thenReturn(false);
        when(userRepository.findAll()).thenReturn(List.of(user("USER-1", "recipient@example.com", "DEPT-1")));
        when(patentReviewService.markMailingSent(List.of()))
                .thenReturn(new PatentReviewService.WorkflowBatchUpdateResult(List.of(), List.of()));

        mailingService.send(new MailingSendRequest(List.of(draft("recipient@example.com", "PAT-1"))));

        ArgumentCaptor<MailingHistoryEntity> captor = ArgumentCaptor.forClass(MailingHistoryEntity.class);
        verify(mailingHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getSentBy()).isEqualTo("admin@syuuk.test");
    }

    @Test
    void rejectsUrlPlaceholdersBeforeSavingHistory() {
        when(mailOAuth2Service.isConnected()).thenReturn(false);

        BusinessReviewMailSendDraft draft = new BusinessReviewMailSendDraft(
                "body",
                List.of(),
                List.of(new BusinessReviewMailPatentSummary("PAT-1", "M-1", "{originalPatentUrl}", "title", null)),
                "recipient@example.com",
                "recipient",
                "subject");

        assertThatThrownBy(() -> mailingService.send(new MailingSendRequest(List.of(draft))))
                .isInstanceOf(PatentFlowException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(mailingHistoryRepository, never()).save(any());
        verify(patentReviewService, never()).markMailingSent(anyList());
    }

    @Test
    void recipientMappingsLoadBusinessUsersOnce() {
        when(departmentRepository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(
                new DepartmentEntity("DEPT-1", "부서1", LocalDate.now()),
                new DepartmentEntity("DEPT-2", "부서2", LocalDate.now())));
        when(userRepository.findByRoleOrderByCreatedAtAsc("BUSINESS")).thenReturn(List.of(
                user("USER-1", "primary@example.com", "DEPT-1"),
                user("USER-2", "cc@example.com", "DEPT-1"),
                user("USER-3", "other@example.com", "DEPT-2")));

        List<DepartmentRecipientMappingResponse> responses = mailingService.getRecipientMappings(null);

        verify(userRepository).findByRoleOrderByCreatedAtAsc("BUSINESS");
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).managerEmail()).isEqualTo("primary@example.com");
        assertThat(responses.get(0).ccEmails()).containsExactly("cc@example.com");
    }

    @Test
    void historyUsesRepositoryPagingAndDbFilters() {
        MailingHistoryEntity entity = new MailingHistoryEntity(
                "MAIL-1",
                "body",
                "[]",
                1,
                "[{\"patentId\":\"PAT-1\",\"managementNumber\":\"M-1\",\"originalPatentUrl\":\"https://patents.google.com/patent/KR1/ko\",\"title\":\"title\"}]",
                "recipient@example.com",
                "recipient",
                "DEPT-1",
                OffsetDateTime.now(),
                "admin@syuuk.test",
                com.syuuk.patentflow.mailing.domain.MailingStatus.SENT,
                "subject");
        when(mailingHistoryRepository.findByRecipientEmailAndPatentIdToken(
                org.mockito.ArgumentMatchers.eq("recipient@example.com"),
                org.mockito.ArgumentMatchers.eq("%\"patentId\":\"PAT-1\"%"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1));

        PageResponse<MailingHistoryItemResponse> response =
                mailingService.getHistory("PAT-1", "recipient@example.com", 1, 10);

        assertThat(response.data()).hasSize(1);
        assertThat(response.page().totalElements()).isEqualTo(1);
        assertThat(response.data().get(0).patents().get(0).originalPatentUrl())
                .isEqualTo("https://patents.google.com/patent/KR1/ko");
    }

    @Test
    void patentIdTokenPatternWrapsTokenBoundaryAndEscapesWildcards() {
        // 토큰 경계("patentId":"<id>")로 감싸고 LIKE 와일드카드(%,_)와 escape 문자(!)를 무력화한다.
        assertThat(MailingService.patentIdTokenPattern("PAT-1"))
                .isEqualTo("%\"patentId\":\"PAT-1\"%");
        assertThat(MailingService.patentIdTokenPattern("PAT_1%"))
                .isEqualTo("%\"patentId\":\"PAT!_1!%\"%");
        assertThat(MailingService.patentIdTokenPattern("a!b"))
                .isEqualTo("%\"patentId\":\"a!!b\"%");
    }

    private static BusinessReviewMailSendDraft draft(String recipientEmail, String patentId) {
        return new BusinessReviewMailSendDraft(
                "body",
                List.of(),
                List.of(new BusinessReviewMailPatentSummary(
                        patentId,
                        "M-" + patentId,
                        "https://patents.google.com/patent/KR" + patentId + "/ko",
                        "title",
                        null)),
                recipientEmail,
                "recipient",
                "subject");
    }

    private static UserEntity user(String id, String email, String departmentId) {
        return new UserEntity(id, email, "encoded", "BUSINESS", departmentId, email);
    }

    private static final class TestMailingService extends MailingService {
        private Set<String> failRecipients = Set.of();

        private TestMailingService(
                MailingHistoryRepository mailingHistoryRepository,
                PatentReviewService patentReviewService,
                ObjectMapper objectMapper,
                UserRepository userRepository,
                SystemSettingsService systemSettingsService,
                DepartmentRepository departmentRepository,
                MailOAuth2Service mailOAuth2Service) {
            super(mailingHistoryRepository, patentReviewService, objectMapper, userRepository,
                    systemSettingsService, departmentRepository, mailOAuth2Service, event -> { });
        }

        @Override
        protected void sendEmailOAuth2(String senderEmail, String accessToken, BusinessReviewMailSendDraft draft) {
            if (failRecipients.contains(draft.recipientEmail())) {
                throw new PatentFlowException(ErrorCode.MAIL_SEND_FAILED);
            }
        }
    }
}
