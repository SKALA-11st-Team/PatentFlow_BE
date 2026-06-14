package com.syuuk.patentflow.mailing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.response.PageInfo;
import com.syuuk.patentflow.common.response.PageResponse;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MailingService {

    private static final Logger log = LoggerFactory.getLogger(MailingService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final com.syuuk.patentflow.mailing.domain.MailingStatus STATUS_SENT =
            com.syuuk.patentflow.mailing.domain.MailingStatus.SENT;
    private static final com.syuuk.patentflow.mailing.domain.MailingStatus STATUS_FAILED =
            com.syuuk.patentflow.mailing.domain.MailingStatus.FAILED;
    private static final com.syuuk.patentflow.mailing.domain.MailingStatus STATUS_RECORDED =
            com.syuuk.patentflow.mailing.domain.MailingStatus.RECORDED;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{?[^}]*url[^}]*}?}", Pattern.CASE_INSENSITIVE);

    private final MailingHistoryRepository mailingHistoryRepository;
    private final PatentReviewService patentReviewService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final SystemSettingsService systemSettingsService;
    private final DepartmentRepository departmentRepository;
    private final MailOAuth2Service mailOAuth2Service;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    // E2E/로컬 검증 전용(기본 false): OAuth2 미연동이어도 draft를 SENT로 기록해 워크플로우 전이를 일으킨다.
    // 운영/데모 환경에서는 켜지 않는다 — 실제 메일은 한 통도 나가지 않는다.
    @org.springframework.beans.factory.annotation.Value("${patentflow.mailing.simulate-delivery:false}")
    private boolean simulateDelivery;

    public MailingService(
            MailingHistoryRepository mailingHistoryRepository,
            PatentReviewService patentReviewService,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            SystemSettingsService systemSettingsService,
            DepartmentRepository departmentRepository,
            MailOAuth2Service mailOAuth2Service,
            org.springframework.context.ApplicationEventPublisher eventPublisher
    ) {
        this.eventPublisher = eventPublisher;
        this.mailingHistoryRepository = mailingHistoryRepository;
        this.patentReviewService = patentReviewService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.systemSettingsService = systemSettingsService;
        this.departmentRepository = departmentRepository;
        this.mailOAuth2Service = mailOAuth2Service;
    }

    @Transactional(readOnly = true)
    public List<DepartmentRecipientMappingResponse> getRecipientMappings(String departmentId) {
        // users.department_id 기준으로 사업부별 수신자를 도출한다.
        // 같은 부서의 첫 번째 BUSINESS 계정(createdAt 오름차순) = 주 수신자, 나머지 = CC
        Map<String, List<UserEntity>> businessUsersByDepartment = userRepository.findByRoleOrderByCreatedAtAsc("BUSINESS")
                .stream()
                .filter(user -> user.getDepartmentId() != null && !user.getDepartmentId().isBlank())
                .collect(Collectors.groupingBy(
                        UserEntity::getDepartmentId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        return departmentRepository.findAll(Sort.by("departmentId")).stream()
                .filter(dept -> departmentId == null || departmentId.isBlank()
                        || departmentId.equals(dept.getDepartmentId()))
                .map(dept -> {
                    List<UserEntity> members = businessUsersByDepartment.getOrDefault(dept.getDepartmentId(), List.of());
                    UserEntity primary = members.isEmpty() ? null : members.get(0);
                    List<String> ccEmails = members.stream().skip(1)
                            .map(UserEntity::getEmail).toList();
                    return toMappingResponse(dept, primary, ccEmails);
                })
                .toList();
    }

    @Transactional
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

    // 트랜잭션 경계(MAIL-02): 이력 저장과 워크플로우 전이를 한 트랜잭션으로 묶어 DB 상태(이력·워크플로우 전이)를
    // 항상 일관되게 유지한다. 실패 처리 방식은 단계별로 다르다 —
    //   토큰 획득(getValidAccessToken): 실패 시 예외를 전파해 전체 롤백(이력 미기록).
    //   개별 발송(sendEmailOAuth2): 1건의 SMTP 실패가 나머지를 막지 않도록 건별 격리하고 FAILED 이력으로 기록한다.
    // 이미 물리적으로 전송된 메일 자체는 되돌릴 수 없으나 DB 상태는 일관되게 유지된다.
    @Transactional
    public MailingSendResponse send(MailingSendRequest request) {
        // OAuth2 연동 우선 → 미발송 기록
        boolean oauth2Connected = mailOAuth2Service.isConnected();

        request.drafts().forEach(this::validateDraft);

        String mailingBatchId = "MAIL-BATCH-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> departmentIdsByEmail = loadDepartmentIdsByEmail(request);
        String sentBy = currentUsername();
        List<BusinessReviewMailSendDraft> sentDrafts = new ArrayList<>();
        int recordedCount = 0;

        int failedCount = 0;
        if (oauth2Connected) {
            String senderEmail = systemSettingsService.getGmailOAuth2ConnectedEmail();
            // 토큰 획득 실패 시 예외를 전파해 전체 롤백한다(이력 미기록).
            mailOAuth2Service.getValidAccessToken();
            // I1: 건별 처리 — 1건의 SMTP 실패가 나머지 발송·이력 기록을 막지 않는다.
            // 실패 건은 FAILED 이력으로 남겨 메일 이력 화면에서 식별·재발송할 수 있게 한다.
            for (BusinessReviewMailSendDraft draft : request.drafts()) {
                try {
                    // 매 건마다 유효 토큰을 재조회한다 — 캐시 적중 시 비용 0, 만료 임박 시 자동 갱신.
                    // 대량/장시간 배치가 토큰 잔여수명을 넘겨도 만료 이후 건이 전량 인증 실패하지 않는다.
                    String accessToken = mailOAuth2Service.getValidAccessToken();
                    sendEmailOAuth2(senderEmail, accessToken, draft);
                    saveHistory(mailingBatchId, draft, STATUS_SENT, departmentIdsByEmail, sentBy);
                    sentDrafts.add(draft);
                } catch (Exception exception) {
                    log.warn("메일 발송 실패 — FAILED 이력 기록 후 다음 건 진행. recipient={}",
                            draft.recipientEmail(), exception);
                    saveHistory(mailingBatchId, draft, STATUS_FAILED, departmentIdsByEmail, sentBy);
                    failedCount++;
                }
            }
        } else if (simulateDelivery) {
            // E2E/로컬 검증 전용 — SMTP 호출 없이 SENT로 기록해 markMailingSent 전이를 검증 가능하게 한다.
            log.warn("patentflow.mailing.simulate-delivery enabled; recording drafts as SENT without SMTP delivery.");
            for (BusinessReviewMailSendDraft draft : request.drafts()) {
                saveHistory(mailingBatchId, draft, STATUS_SENT, departmentIdsByEmail, sentBy);
                sentDrafts.add(draft);
            }
        } else {
            // OAuth2 미연동 — 실제 발송 없이 이력만 기록한다. RECORDED는 워크플로우 전이 대상이 아니다.
            log.info("Gmail OAuth2 is not connected; recording mailing workflow without SMTP delivery.");
            for (BusinessReviewMailSendDraft draft : request.drafts()) {
                saveHistory(mailingBatchId, draft, STATUS_RECORDED, departmentIdsByEmail, sentBy);
                recordedCount++;
            }
        }

        // 발송 성공분(SENT)만 사업부 회신 대기 상태로 전이한다. RECORDED는 상태를 유지한다.
        List<String> patentIds = sentDrafts.stream()
                .flatMap(draft -> draft.patents().stream())
                .map(BusinessReviewMailPatentSummary::patentId)
                .distinct()
                .toList();
        PatentReviewService.WorkflowBatchUpdateResult updateResult = patentReviewService.markMailingSent(patentIds);

        // NOTI-04: 실제 발송분(SENT)이 있을 때만 알림 발행 — 관리자(발송 완료)·사업부(검토 요청 도착).
        if (!sentDrafts.isEmpty()) {
            eventPublisher.publishEvent(new com.syuuk.patentflow.notification.event.WorkflowNotificationEvent(
                    "사업부 메일 발송 완료",
                    "검토 요청 메일이 발송되었습니다(특허 %d건).".formatted(patentIds.size()),
                    "ADMIN",
                    "/admin/mailing"));
            eventPublisher.publishEvent(new com.syuuk.patentflow.notification.event.WorkflowNotificationEvent(
                    "검토 요청 도착",
                    "연차료 검토 요청 메일이 도착했습니다. 배정 특허를 확인해주세요.",
                    "BUSINESS",
                    "/business/review-requests"));
        }

        return new MailingSendResponse(
                mailingBatchId,
                updateResult.updatedPatentIds().size(),
                updateResult.updatedPatentIds(),
                updateResult.skippedPatentIds(),
                sentDrafts.size(),
                failedCount,
                recordedCount);
    }

    // OAuth2 XOAUTH2 방식 — access_token을 비밀번호로 사용해 Gmail SMTP 인증
    protected void sendEmailOAuth2(String senderEmail, String accessToken, BusinessReviewMailSendDraft draft) {
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

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

    @Transactional(readOnly = true)
    public PageResponse<MailingHistoryItemResponse> getHistory(String patentId, String recipientEmail, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(normalizedPage - 1, normalizedSize, Sort.by(Sort.Direction.DESC, "sentAt"));
        boolean hasPatentId = patentId != null && !patentId.isBlank();
        boolean hasRecipientEmail = recipientEmail != null && !recipientEmail.isBlank();
        Page<MailingHistoryEntity> entityPage;
        if (hasPatentId && hasRecipientEmail) {
            entityPage = mailingHistoryRepository.findByRecipientEmailAndPatentIdToken(
                    recipientEmail.trim(), patentIdTokenPattern(patentId.trim()), pageable);
        } else if (hasRecipientEmail) {
            entityPage = mailingHistoryRepository.findByRecipientEmailIgnoreCase(recipientEmail.trim(), pageable);
        } else if (hasPatentId) {
            entityPage = mailingHistoryRepository.findByPatentIdToken(patentIdTokenPattern(patentId.trim()), pageable);
        } else {
            entityPage = mailingHistoryRepository.findAll(pageable);
        }

        return PageResponse.ok(
                entityPage.getContent().stream().map(this::toHistoryResponse).toList(),
                new PageInfo(normalizedPage, normalizedSize, entityPage.getTotalElements(), entityPage.getTotalPages()));
    }

    // 회귀: patentsJson 안의 "patentId":"<id>" 토큰을 정확 매칭하기 위한 LIKE 패턴을 만든다.
    // 닫는 따옴표까지 포함해 접두/교차필드 충돌을 막고, 입력의 LIKE 와일드카드(%,_)와 escape 문자(!)를 무력화한다.
    static String patentIdTokenPattern(String patentId) {
        String escaped = patentId
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%\"patentId\":\"" + escaped + "\"%";
    }

    private void saveHistory(
            String mailingBatchId,
            BusinessReviewMailSendDraft draft,
            com.syuuk.patentflow.mailing.domain.MailingStatus status,
            Map<String, String> departmentIdsByEmail,
            String sentBy
    ) {
        String mailingId = mailingBatchId + "-" + UUID.randomUUID().toString().replace("-", "");
        String departmentId = departmentIdsByEmail.get(normalizeEmail(draft.recipientEmail()));
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
                sentBy,
                status,
                draft.subject()));
    }

    private Map<String, String> loadDepartmentIdsByEmail(MailingSendRequest request) {
        Map<String, String> requestedEmails = request.drafts().stream()
                .map(BusinessReviewMailSendDraft::recipientEmail)
                .filter(email -> email != null && !email.isBlank())
                .collect(Collectors.toMap(
                        this::normalizeEmail,
                        email -> email,
                        (existing, ignored) -> existing,
                        LinkedHashMap::new));
        if (requestedEmails.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAll().stream()
                .filter(user -> user.getEmail() != null && requestedEmails.containsKey(normalizeEmail(user.getEmail())))
                .collect(Collectors.toMap(
                        user -> normalizeEmail(user.getEmail()),
                        UserEntity::getDepartmentId,
                        (existing, ignored) -> existing));
    }

    private void validateDraft(BusinessReviewMailSendDraft draft) {
        for (BusinessReviewMailPatentSummary patent : draft.patents()) {
            String originalPatentUrl = patent.originalPatentUrl();
            if (originalPatentUrl == null || originalPatentUrl.isBlank()
                    || PLACEHOLDER_PATTERN.matcher(originalPatentUrl).find()) {
                throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                        "메일 특허 요약에는 실제 원문 URL이 필요합니다: " + patent.patentId());
            }
        }
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "PatentFlow";
        }
        // principal이 UserPrincipalResponse(record)일 때 getName()은 record toString() 전문을
        // 반환해 sent_by varchar(128)을 초과 — SMTP 발송 성공 후 이력 저장이 깨지던 결함(E2E 실증).
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.syuuk.patentflow.auth.dto.UserPrincipalResponse userPrincipal) {
            if (userPrincipal.username() != null && !userPrincipal.username().isBlank()) {
                return userPrincipal.username().trim();
            }
            if (userPrincipal.email() != null && !userPrincipal.email().isBlank()) {
                return userPrincipal.email().trim();
            }
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? "PatentFlow" : name.trim();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
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
                entity.getStatus() == null ? null : entity.getStatus().name(),
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
