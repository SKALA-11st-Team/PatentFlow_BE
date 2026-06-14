package com.syuuk.patentflow.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.invitation.domain.InvitationEntity;
import com.syuuk.patentflow.invitation.domain.InvitationStatus;
import com.syuuk.patentflow.invitation.service.InvitationService;
import com.syuuk.patentflow.mailing.repository.DepartmentRepository;
import com.syuuk.patentflow.mailing.service.MailOAuth2Service;
import com.syuuk.patentflow.settings.service.SettingsService;
import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.dto.CreateUserRequest;
import com.syuuk.patentflow.user.dto.ResetPasswordResponse;
import com.syuuk.patentflow.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SystemSettingsService systemSettingsService;

    @Mock
    private MailOAuth2Service mailOAuth2Service;

    @Mock
    private InvitationService invitationService;

    @Mock
    private SettingsService settingsService;

    private TestAdminUserService service;

    @BeforeEach
    void setUp() {
        service = new TestAdminUserService(userRepository, departmentRepository, passwordEncoder,
                systemSettingsService, mailOAuth2Service, invitationService, settingsService);
    }

    private static InvitationEntity stubInvitation(UserEntity user) {
        return new InvitationEntity("INV-TEST", user.getId(), user.getDepartmentId(), "hash",
                InvitationStatus.PENDING, null,
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now().plusDays(7));
    }

    @Test
    void createAdminUsesExistsByRoleInsteadOfFullTableScan() {
        when(userRepository.existsByRole("ADMIN")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(new CreateUserRequest(
                "new-admin@test.com", "ADMIN", null, "관리자")))
                .isInstanceOf(PatentFlowException.class)
                .hasMessageContaining("관리자 계정은 1개만 허용");

        verify(userRepository).existsByRole("ADMIN");
        verify(userRepository, never()).findAll();
    }

    @Test
    void updateUserRejectsAdminPromotionWhenAnotherAdminExists() {
        UserEntity businessUser = user("USER-business", "business@test.com", "BUSINESS", "DEPT-ICT");
        when(userRepository.findById("USER-business")).thenReturn(Optional.of(businessUser));
        when(userRepository.findByEmail("business@test.com")).thenReturn(Optional.of(businessUser));
        when(userRepository.existsByRoleAndIdNot("ADMIN", "USER-business")).thenReturn(true);

        assertThatThrownBy(() -> service.updateUser("USER-business", new CreateUserRequest(
                "business@test.com", "ADMIN", null, "사업부")))
                .isInstanceOf(PatentFlowException.class)
                .hasMessageContaining("관리자 계정은 1개만 허용");

        verify(userRepository, never()).save(any());
    }

    @Test
    void createBusinessUserRejectsUnknownDepartment() {
        when(departmentRepository.existsById("DEPT-MISSING")).thenReturn(false);

        assertThatThrownBy(() -> service.createUser(new CreateUserRequest(
                "business@test.com", "BUSINESS", "DEPT-MISSING", "사업부")))
                .isInstanceOf(PatentFlowException.class)
                .hasMessageContaining("존재하지 않는 사업부");

        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteUserRejectsSelfDeletionAndProtectedAdmin() {
        UserEntity currentAdmin = user("USER-current", "admin@test.com", "ADMIN", null);
        when(userRepository.findById("USER-current")).thenReturn(Optional.of(currentAdmin));

        assertThatThrownBy(() -> service.deleteUser("USER-current", "USER-current"))
                .isInstanceOf(PatentFlowException.class)
                .hasMessageContaining("자기 자신의 계정");

        UserEntity bootstrapAdmin = user("USER-admin-bootstrap", "bootstrap@test.com", "ADMIN", null);
        when(userRepository.findById("USER-admin-bootstrap")).thenReturn(Optional.of(bootstrapAdmin));

        assertThatThrownBy(() -> service.deleteUser("USER-admin-bootstrap", "USER-current"))
                .isInstanceOf(PatentFlowException.class)
                .hasMessageContaining("기본 관리자 계정");

        verify(userRepository, never()).delete(any());
    }

    @Test
    void resetPasswordRejectsProtectedAdminAndDoesNotExposePlainPassword() {
        UserEntity bootstrapAdmin = user("USER-admin-bootstrap", "bootstrap@test.com", "ADMIN", null);
        when(userRepository.findById("USER-admin-bootstrap")).thenReturn(Optional.of(bootstrapAdmin));

        assertThatThrownBy(() -> service.resetPassword("USER-admin-bootstrap"))
                .isInstanceOf(PatentFlowException.class)
                .hasMessageContaining("비밀번호는 초기화할 수 없습니다");

        UserEntity businessUser = user("USER-business", "business@test.com", "BUSINESS", "DEPT-ICT");
        when(userRepository.findById("USER-business")).thenReturn(Optional.of(businessUser));
        when(passwordEncoder.encode(any())).thenReturn("encoded-temp-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResetPasswordResponse response = service.resetPassword("USER-business");

        assertThat(response.userId()).isEqualTo("USER-business");
        assertThat(response.email()).isEqualTo("business@test.com");
        assertThat(response.temporaryPassword()).isEqualTo("************");
        assertThat(response.emailSent()).isTrue();
        assertThat(response.message()).contains("이메일");
        assertThat(service.lastTempPassword).isNotBlank();
        assertThat(response.toString()).doesNotContain(service.lastTempPassword);
    }

    @Test
    void createBusinessUserPersistsOnlyValidatedDepartmentId() {
        when(userRepository.existsByEmail("business@test.com")).thenReturn(false);
        when(departmentRepository.existsById("DEPT-ICT")).thenReturn(true);
        when(passwordEncoder.encode(any())).thenReturn("encoded-temp-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitationService.createInvitation(any(UserEntity.class), any()))
                .thenAnswer(invocation -> new InvitationService.CreatedInvitation(
                        stubInvitation(invocation.getArgument(0)), "raw-token"));

        service.createUser(new CreateUserRequest("business@test.com", "BUSINESS", " DEPT-ICT ", "사업부"));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getDepartmentId()).isEqualTo("DEPT-ICT");
    }

    private static UserEntity user(String id, String email, String role, String departmentId) {
        return new UserEntity(id, email, "encoded", role, departmentId, "사용자");
    }

    private static class TestAdminUserService extends AdminUserService {

        private String lastTempPassword;

        TestAdminUserService(
                UserRepository userRepository,
                DepartmentRepository departmentRepository,
                PasswordEncoder passwordEncoder,
                SystemSettingsService systemSettingsService,
                MailOAuth2Service mailOAuth2Service,
                InvitationService invitationService,
                SettingsService settingsService) {
            super(userRepository, departmentRepository, passwordEncoder, systemSettingsService, mailOAuth2Service,
                    invitationService, settingsService);
        }

        @Override
        protected void sendEmail(String to, String subject, String body, String tempPassword) {
            this.lastTempPassword = tempPassword;
        }
    }
}
