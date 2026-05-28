package com.syuuk.patentflow.mailing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.mailing.domain.MailingHistoryEntity;
import com.syuuk.patentflow.mailing.dto.BusinessReviewMailPatentSummary;
import com.syuuk.patentflow.mailing.dto.DepartmentRecipientMappingRequest;
import com.syuuk.patentflow.mailing.dto.BusinessReviewMailSendDraft;
import com.syuuk.patentflow.mailing.dto.DepartmentRecipientMappingResponse;
import com.syuuk.patentflow.mailing.dto.MailingHistoryItemResponse;
import com.syuuk.patentflow.mailing.dto.MailingSendRequest;
import com.syuuk.patentflow.mailing.dto.MailingSendResponse;
import com.syuuk.patentflow.mailing.domain.DepartmentEntity;
import com.syuuk.patentflow.mailing.repository.DepartmentRepository;
import com.syuuk.patentflow.mailing.repository.MailingHistoryRepository;
import com.syuuk.patentflow.patent.service.PatentReviewService;
import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

@Service
public class MailingService {

    private static final Logger log = LoggerFactory.getLogger(MailingService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_RECORDED = "RECORDED";

    @Value("${spring.mail.username:}")
    private String envGmailUsername;

    @Value("${spring.mail.password:}")
    private String envGmailAppPassword;

    private final MailingHistoryRepository mailingHistoryRepository;
    private final PatentReviewService patentReviewService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final SystemSettingsService systemSettingsService;
    private final DepartmentRepository departmentRepository;
    private final MailOAuth2Service mailOAuth2Service;

    public MailingService(
            MailingHistoryRepository mailingHistoryRepository,
            PatentReviewService patentReviewService,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            SystemSettingsService systemSettingsService,
            DepartmentRepository departmentRepository,
            MailOAuth2Service mailOAuth2Service
    ) {
        this.mailingHistoryRepository = mailingHistoryRepository;
        this.patentReviewService = patentReviewService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.systemSettingsService = systemSettingsService;
        this.departmentRepository = departmentRepository;
        this.mailOAuth2Service = mailOAuth2Service;
    }

    public List<DepartmentRecipientMappingResponse> getRecipientMappings(String departmentId) {
        // users.department_id 기준으로 사업부별 수신자를 도출한다.
        // 같은 부서의 첫 번째 BUSINESS 계정(createdAt 오름차순) = 주 수신자, 나머지 = CC
        return departmentRepository.findAll(Sort.by("departmentId")).stream()
                .filter(dept -> departmentId == null || departmentId.isBlank()
                        || departmentId.equals(dept.getDepartmentId()))
                .map(dept -> {
                    List<UserEntity> members = userRepository
                            .findAll(Sort.by("createdAt"))
                            .stream()
                            .filter(u -> "BUSINESS".equals(u.getRole())
                                    && dept.getDepartmentId().equals(u.getDepartmentId()))
                            .toList();
                    UserEntity primary = members.isEmpty() ? null : members.get(0);
                    List<String> ccEmails = members.stream().skip(1)
                            .map(UserEntity::getEmail).toList();
                    return toMappingResponse(dept, primary, ccEmails);
                })
                .toList();
    }

    // 부서명만 변경 가능 — 수신자 이메일은 users 테이블에서 파생되므로 여기서 관리하지 않는다.
    public DepartmentRecipientMappingResponse updateRecipientMapping(
            String departmentId,
            DepartmentRecipientMappingRequest request
    ) {
        DepartmentEntity department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "사업부를 찾을 수 없습니다: " + departmentId));
        if (request.departmentName() != null && !request.departmentName().isBlank()) {
            department.rename(request.departmentName(), LocalDate.now(KST));
            departmentRepository.save(department);
        }
        return getRecipientMappings(departmentId).get(0);
    }

    public MailingSendResponse send(MailingSendRequest request) {
        // OAuth2 연동 우선 → 앱 비밀번호(레거시) → 미발송 기록
        boolean oauth2Connected = mailOAuth2Service.isConnected();
        String username = resolve(systemSettingsService.getGmailUsername(), envGmailUsername);
        String appPassword = resolve(systemSettingsService.getGmailAppPassword(), envGmailAppPassword);
        boolean appPasswordConfigured = username != null && !username.isBlank()
                && appPassword != null && !appPassword.isBlank();

        String mailingBatchId = "MAIL-BATCH-" + System.currentTimeMillis();

        if (oauth2Connected) {
            String senderEmail = systemSettingsService.getGmailOAuth2ConnectedEmail();
            String accessToken = mailOAuth2Service.getValidAccessToken();
            request.drafts().forEach(draft -> {
                try {
                    sendEmailOAuth2(senderEmail, accessToken, draft);
                    saveHistory(mailingBatchId, draft, STATUS_SENT);
                } catch (PatentFlowException exception) {
                    saveHistory(mailingBatchId, draft, STATUS_FAILED);
                    throw exception;
                }
            });
        } else if (appPasswordConfigured) {
            request.drafts().forEach(draft -> {
                try {
                    sendEmail(username, appPassword, draft);
                    saveHistory(mailingBatchId, draft, STATUS_SENT);
                } catch (PatentFlowException exception) {
                    saveHistory(mailingBatchId, draft, STATUS_FAILED);
                    throw exception;
                }
            });
        } else {
            // OAuth2·앱비밀번호 모두 미연동 — 실제 발송 없이 이력만 기록(RECORDED) 해 워크플로우는 유지
            log.info("Gmail credentials are not configured; recording mailing workflow without SMTP delivery.");
            request.drafts().forEach(draft -> saveHistory(mailingBatchId, draft, STATUS_RECORDED));
        }

        // 발송 성공 후 상태 변경 및 이력 저장
        List<String> patentIds = request.drafts().stream()
                .flatMap(draft -> draft.patents().stream())
                .map(BusinessReviewMailPatentSummary::patentId)
                .distinct()
                .toList();
        PatentReviewService.WorkflowBatchUpdateResult updateResult = patentReviewService.markMailingSent(patentIds);

        return new MailingSendResponse(
                mailingBatchId,
                updateResult.updatedPatentIds().size(),
                updateResult.updatedPatentIds(),
                updateResult.skippedPatentIds());
    }

    // OAuth2 XOAUTH2 방식 — access_token을 비밀번호로 사용해 Gmail SMTP 인증
    private void sendEmailOAuth2(String senderEmail, String accessToken, BusinessReviewMailSendDraft draft) {
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");

        try {
            Session session = Session.getInstance(props);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderEmail));
            message.setRecipients(MimeMessage.RecipientType.TO,
                    InternetAddress.parse(draft.recipientEmail()));
            List<String> ccEmails = normalizedCcEmails(draft);
            if (!ccEmails.isEmpty()) {
                message.setRecipients(MimeMessage.RecipientType.CC,
                        InternetAddress.parse(String.join(",", ccEmails)));
            }
            message.setSubject(draft.subject(), "UTF-8");
            message.setText(draft.body(), "UTF-8");
            message.saveChanges();

            Transport transport = session.getTransport("smtp");
            transport.connect("smtp.gmail.com", 587, senderEmail, accessToken);
            transport.sendMessage(message, message.getAllRecipients());
            transport.close();
            log.info("Email sent via OAuth2 to {} (subject={})", draft.recipientEmail(), draft.subject());
        } catch (Exception e) {
            log.warn("Failed to send OAuth2 email to {}: {}", draft.recipientEmail(), e.getMessage());
            throw new PatentFlowException(ErrorCode.MAIL_SEND_FAILED);
        }
    }

    private void sendEmail(String username, String password, BusinessReviewMailSendDraft draft) {
        try {
            JavaMailSenderImpl sender = buildSender(username, password);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(username);
            helper.setTo(draft.recipientEmail());
            List<String> ccEmails = normalizedCcEmails(draft);
            if (!ccEmails.isEmpty()) {
                helper.setCc(ccEmails.toArray(String[]::new));
            }
            helper.setSubject(draft.subject());
            helper.setText(draft.body(), false);
            sender.send(message);
            log.info("Email sent to {} (subject={})", draft.recipientEmail(), draft.subject());
        } catch (MessagingException e) {
            log.warn("Failed to send email to {}: {}", draft.recipientEmail(), e.getMessage());
            throw new PatentFlowException(ErrorCode.MAIL_SEND_FAILED);
        }
    }

    private static String resolve(String fromDb, String fromEnv) {
        return (fromDb != null && !fromDb.isBlank()) ? fromDb : fromEnv;
    }

    private static JavaMailSenderImpl buildSender(String username, String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.gmail.com");
        sender.setPort(587);
        sender.setUsername(username);
        sender.setPassword(password);
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        return sender;
    }

    public List<MailingHistoryItemResponse> getHistory(String patentId, String recipientEmail) {
        return mailingHistoryRepository.findAll(Sort.by(Sort.Direction.DESC, "sentAt")).stream()
                .map(this::toHistoryResponse)
                .filter(history -> recipientEmail == null || recipientEmail.isBlank()
                        || history.recipientEmail().equalsIgnoreCase(recipientEmail))
                .filter(history -> patentId == null || patentId.isBlank()
                        || history.patents().stream().anyMatch(patent -> patent.patentId().equals(patentId)))
                .toList();
    }

    private void saveHistory(String mailingBatchId, BusinessReviewMailSendDraft draft, String status) {
        // 배치ID + 수신자 해시로 이력 ID 생성 — 같은 배치 내 수신자별 고유성 보장
        String mailingId = mailingBatchId + "-" + Math.abs(draft.recipientEmail().hashCode());
        // 이력에 부서ID를 남기기 위해 users 테이블에서 수신자 이메일로 역조회
        String departmentId = userRepository.findByEmail(draft.recipientEmail())
                .map(u -> u.getDepartmentId())
                .orElse(null);
        mailingHistoryRepository.save(new MailingHistoryEntity(
                mailingId,
                draft.body(),
                writeJson(normalizedCcEmails(draft)),
                draft.patents().size(),
                writeJson(draft.patents()),
                draft.recipientEmail(),
                draft.recipientName(),
                departmentId,
                OffsetDateTime.now(KST),
                "PatentFlow",
                status,
                draft.subject()));
    }

    private List<String> normalizedCcEmails(BusinessReviewMailSendDraft draft) {
        if (draft.ccEmails() == null) {
            return List.of();
        }
        return draft.ccEmails().stream()
                .filter(email -> email != null && !email.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    // 이메일·이름·CC는 모두 users 테이블에서 파생 — departments에 별도 저장하지 않는다.
    private DepartmentRecipientMappingResponse toMappingResponse(
            DepartmentEntity department,
            UserEntity primary,
            List<String> ccEmails
    ) {
        String departmentId = department.getDepartmentId();
        String departmentName = department.getDepartmentName() != null
                ? department.getDepartmentName() : departmentId;
        String managerEmail = primary != null ? primary.getEmail() : "";
        String managerName = primary != null && primary.getUsername() != null
                ? primary.getUsername() : "";
        String updatedAt = department.getUpdatedAt() != null
                ? department.getUpdatedAt().toString() : "";

        return new DepartmentRecipientMappingResponse(
                departmentId, departmentName,
                managerEmail, managerName,
                ccEmails, updatedAt);
    }

    private MailingHistoryItemResponse toHistoryResponse(MailingHistoryEntity entity) {
        return new MailingHistoryItemResponse(
                entity.getBody(),
                readStringList(entity.getCcEmailsJson()),
                entity.getMailingId(),
                entity.getPatentCount(),
                readPatentSummaries(entity.getPatentsJson()),
                entity.getRecipientEmail(),
                entity.getRecipientName(),
                entity.getSentAt(),
                entity.getSentBy(),
                entity.getStatus(),
                entity.getSubject());
    }

    private List<String> readStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<BusinessReviewMailPatentSummary> readPatentSummaries(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("메일 데이터를 JSON으로 저장할 수 없습니다.", exception);
        }
    }

}
