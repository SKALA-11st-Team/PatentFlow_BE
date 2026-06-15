package com.syuuk.patentflow.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.mailing.domain.DepartmentEntity;
import com.syuuk.patentflow.mailing.dto.DepartmentRecipientMappingResponse;
import com.syuuk.patentflow.mailing.repository.DepartmentRepository;
import com.syuuk.patentflow.mailing.repository.MailingHistoryRepository;
import com.syuuk.patentflow.patent.repository.PatentReviewHistoryRepository;
import com.syuuk.patentflow.patent.service.PatentReviewService;
import com.syuuk.patentflow.user.dto.CreateDepartmentRequest;
import com.syuuk.patentflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PatentReviewService patentReviewService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatentReviewHistoryRepository patentReviewHistoryRepository;

    @Mock
    private MailingHistoryRepository mailingHistoryRepository;

    private AdminDepartmentService service;

    @BeforeEach
    void setUp() {
        service = new AdminDepartmentService(departmentRepository, patentReviewService, userRepository,
                patentReviewHistoryRepository, mailingHistoryRepository);
    }

    @Test
    void createDepartmentResponseUsesPersistedEntityTimestamp() {
        when(departmentRepository.existsById("DEPT-NEW")).thenReturn(false);

        DepartmentRecipientMappingResponse response =
                service.createDepartment(new CreateDepartmentRequest("DEPT-NEW", "신규 사업부"));

        ArgumentCaptor<DepartmentEntity> captor = ArgumentCaptor.forClass(DepartmentEntity.class);
        verify(departmentRepository).save(captor.capture());
        DepartmentEntity saved = captor.getValue();

        // 응답 updatedAt이 저장된 엔티티 시각과 정확히 일치(LocalDate.now() 재호출 없음)
        assertThat(response.departmentId()).isEqualTo("DEPT-NEW");
        assertThat(response.departmentName()).isEqualTo("신규 사업부");
        assertThat(response.updatedAt()).isEqualTo(saved.getUpdatedAt().toString());
    }

    @Test
    void deleteDepartmentRejectsUserReference() {
        when(departmentRepository.existsById("DEPT-ICT")).thenReturn(true);
        when(userRepository.existsByDepartmentId("DEPT-ICT")).thenReturn(true);

        assertThatThrownBy(() -> service.deleteDepartment("DEPT-ICT"))
                .isInstanceOf(PatentFlowException.class)
                .hasMessageContaining("소속된 계정");

        verify(departmentRepository, never()).deleteById("DEPT-ICT");
    }

    @Test
    void deleteDepartmentRejectsPatentReviewHistoryReference() {
        when(departmentRepository.existsById("DEPT-ICT")).thenReturn(true);
        when(userRepository.existsByDepartmentId("DEPT-ICT")).thenReturn(false);
        when(patentReviewHistoryRepository.existsByDepartmentId("DEPT-ICT")).thenReturn(true);

        assertThatThrownBy(() -> service.deleteDepartment("DEPT-ICT"))
                .isInstanceOf(PatentFlowException.class)
                .hasMessageContaining("특허 검토 이력");

        verify(departmentRepository, never()).deleteById("DEPT-ICT");
    }

    @Test
    void deleteDepartmentRejectsMailingHistoryReference() {
        when(departmentRepository.existsById("DEPT-ICT")).thenReturn(true);
        when(userRepository.existsByDepartmentId("DEPT-ICT")).thenReturn(false);
        when(patentReviewHistoryRepository.existsByDepartmentId("DEPT-ICT")).thenReturn(false);
        when(mailingHistoryRepository.existsByDepartmentId("DEPT-ICT")).thenReturn(true);

        assertThatThrownBy(() -> service.deleteDepartment("DEPT-ICT"))
                .isInstanceOf(PatentFlowException.class)
                .hasMessageContaining("메일 발송 이력");

        verify(departmentRepository, never()).deleteById("DEPT-ICT");
    }
}
