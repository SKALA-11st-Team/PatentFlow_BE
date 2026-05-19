package com.syuuk.patentflow.mailing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.mailing.domain.MailingHistoryEntity;
import com.syuuk.patentflow.mailing.dto.BusinessReviewMailPatentSummary;
import com.syuuk.patentflow.mailing.dto.BusinessReviewMailSendDraft;
import com.syuuk.patentflow.mailing.dto.DepartmentRecipientMappingResponse;
import com.syuuk.patentflow.mailing.dto.MailingHistoryItemResponse;
import com.syuuk.patentflow.mailing.dto.MailingSendRequest;
import com.syuuk.patentflow.mailing.dto.MailingSendResponse;
import com.syuuk.patentflow.mailing.repository.MailingHistoryRepository;
import com.syuuk.patentflow.patent.service.PatentReviewService;
import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
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

    public MailingService(
            MailingHistoryRepository mailingHistoryRepository,
            PatentReviewService patentReviewService,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            SystemSettingsService systemSettingsService
    ) {
        this.mailingHistoryRepository = mailingHistoryRepository;
        this.patentReviewService = patentReviewService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.systemSettingsService = systemSettingsService;
    }

    public List<DepartmentRecipientMappingResponse> getRecipientMappings(String departmentId) {
        return userRepository.findAll(Sort.by("departmentId", "createdAt")).stream()
                .filter(u -> "BUSINESS".equals(u.getRole()) && u.getDepartmentId() != null)
                .filter(u -> departmentId == null || departmentId.isBlank()
                        || departmentId.equals(u.getDepartmentId()))
                .collect(Collectors.groupingBy(UserEntity::getDepartmentId,
                        LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    List<UserEntity> users = entry.getValue();
                    UserEntity primary = users.get(0);
                    List<String> ccEmails = users.stream().skip(1)
                            .map(UserEntity::getUsername).toList();
                    return new DepartmentRecipientMappingResponse(
                            entry.getKey(),
                            primary.getDepartmentName() != null ? primary.getDepartmentName() : entry.getKey(),
                            primary.getUsername(),
                            primary.getDisplayName(),
                            ccEmails,
                            primary.getCreatedAt() != null ? primary.getCreatedAt().toLocalDate().toString() : "");
                })
                .toList();
    }

    public MailingSendResponse send(MailingSendRequest request) {
        String username = resolve(systemSettingsService.getGmailUsername(), envGmailUsername);
        String password = resolve(systemSettingsService.getGmailAppPassword(), envGmailAppPassword);
        boolean smtpConfigured = username != null && !username.isBlank() && password != null && !password.isBlank();
        String mailingBatchId = "MAIL-BATCH-" + System.currentTimeMillis();

        if (smtpConfigured) {
            request.drafts().forEach(draft -> {
                try {
                    sendEmail(username, password, draft);
                    saveHistory(mailingBatchId, draft, STATUS_SENT);
                } catch (PatentFlowException exception) {
                    saveHistory(mailingBatchId, draft, STATUS_FAILED);
                    throw exception;
                }
            });
        } else {
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
        String mailingId = mailingBatchId + "-" + Math.abs(draft.recipientEmail().hashCode());
        String departmentId = userRepository.findByUsername(draft.recipientEmail())
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
