package com.syuuk.patentflow.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.common.domain.SystemSettingsEntity;
import com.syuuk.patentflow.common.dto.ClassificationResponse;
import com.syuuk.patentflow.common.dto.CountryExtensionRequest;
import com.syuuk.patentflow.common.dto.CountryExtensionResponse;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.repository.SystemSettingsRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemSettingsService {

    // OAuth2 앱 자격증명 — 관리자 UI에서 입력, env 폴백 지원
    static final String KEY_GMAIL_OAUTH2_CLIENT_ID = "mail.gmail.oauth2.client_id";
    static final String KEY_GMAIL_OAUTH2_CLIENT_SECRET = "mail.gmail.oauth2.client_secret";
    // OAuth2 연동 토큰 — refresh_token은 영구 보관, connected_email은 UI 표시용
    static final String KEY_GMAIL_OAUTH2_REFRESH_TOKEN = "mail.gmail.oauth2.refresh_token";
    static final String KEY_GMAIL_OAUTH2_CONNECTED_EMAIL = "mail.gmail.oauth2.connected_email";
    static final String KEY_MAIL_LEAD_MONTHS = "review.mail.lead_months";
    // 회신 기한 = 검토 시작일(활성화일) + N개월 + M일. 개월과 일을 분리 저장해 세밀한 설정 허용
    static final String KEY_RESPONSE_DEADLINE_MONTHS = "review.response.deadline.months";
    static final String KEY_RESPONSE_DEADLINE_DAYS = "review.response.deadline.days";
    private static final String KEY_COUNTRY_EXT_PREFIX = "country.extension.";
    private static final String KEY_FEE_RULE_PREFIX = "fee.rule.";
    private static final String KEY_CLASSIFICATION_PREFIX = "classification.";

    private static final List<String> SUPPORTED_COUNTRIES = List.of("KR", "JP", "CN", "US", "TW");
    private static final Map<String, String> COUNTRY_LABELS = Map.of(
            "KR", "한국 (KR)",
            "JP", "일본 (JP)",
            "CN", "중국 (CN)",
            "US", "미국 (US)",
            "TW", "대만 (TW)"
    );
    private static final int DEFAULT_EXTENSION_MONTHS = 12;
    private static final int DEFAULT_MAIL_LEAD_MONTHS = 2;
    private static final int DEFAULT_RESPONSE_DEADLINE_MONTHS = 1;
    private static final int DEFAULT_RESPONSE_DEADLINE_DAYS = 0;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final List<String> DEFAULT_BUSINESS_CLASSIFICATIONS = List.of(
            "AI", "Data", "Blockchain", "Cloud", "ESG", "제조", "통신", "금융/전략", "통합서비스", "기존 사업");
    private static final List<String> DEFAULT_TECHNOLOGY_CLASSIFICATIONS = List.of(
            "데이터분석", "AB Testing", "Blockchain", "Cloud", "시스템 운영", "장애관리", "Green IT (AMR)",
            "인증", "보안", "Network", "NFC", "AR", "VR (Avatar)", "사내시스템");

    private final SystemSettingsRepository repository;
    // MAIL-08: 민감 시크릿(refresh_token·client_secret) 저장/조회 시 AES-GCM 적용.
    private final com.syuuk.patentflow.common.security.SecretCipher secretCipher;
    // env 폴백 — DB에 값이 없을 때 환경변수 값을 사용 (Docker 배포 시 편의)
    @Value("${GOOGLE_OAUTH2_CLIENT_ID:}")
    private String envGoogleClientId;
    @Value("${GOOGLE_OAUTH2_CLIENT_SECRET:}")
    private String envGoogleClientSecret;

    public SystemSettingsService(
            SystemSettingsRepository repository,
            com.syuuk.patentflow.common.security.SecretCipher secretCipher) {
        this.repository = repository;
        this.secretCipher = secretCipher;
    }

    @Transactional(readOnly = true)
    public String get(String key) {
        return repository.findById(key).map(SystemSettingsEntity::getValue).orElse(null);
    }

    @Transactional
    public void set(String key, String value) {
        SystemSettingsEntity entity = repository.findById(key).orElse(new SystemSettingsEntity(key));
        entity.setValue(value);
        repository.save(entity);
    }

    // ── Gmail OAuth2 앱 자격증명 ──────────────────────────────
    // DB 값 우선, 없으면 env 폴백 — 관리자 UI 입력 또는 Docker env 모두 지원

    public String getGmailOAuth2ClientId() {
        String dbValue = get(KEY_GMAIL_OAUTH2_CLIENT_ID);
        return (dbValue != null && !dbValue.isBlank()) ? dbValue : envGoogleClientId;
    }

    public String getGmailOAuth2ClientSecret() {
        String dbValue = secretCipher.decrypt(get(KEY_GMAIL_OAUTH2_CLIENT_SECRET));
        return (dbValue != null && !dbValue.isBlank()) ? dbValue : envGoogleClientSecret;
    }

    // ── Gmail OAuth2 연동 토큰 ────────────────────────────────

    public String getGmailOAuth2RefreshToken() {
        return secretCipher.decrypt(get(KEY_GMAIL_OAUTH2_REFRESH_TOKEN));
    }

    public String getGmailOAuth2ConnectedEmail() {
        return get(KEY_GMAIL_OAUTH2_CONNECTED_EMAIL);
    }

    public void saveGmailOAuth2(String refreshToken, String email) {
        set(KEY_GMAIL_OAUTH2_REFRESH_TOKEN, secretCipher.encrypt(refreshToken));
        set(KEY_GMAIL_OAUTH2_CONNECTED_EMAIL, email);
    }

    public void clearGmailOAuth2() {
        set(KEY_GMAIL_OAUTH2_REFRESH_TOKEN, "");
        set(KEY_GMAIL_OAUTH2_CONNECTED_EMAIL, "");
    }

    public int getMailLeadMonths() {
        String value = get(KEY_MAIL_LEAD_MONTHS);
        if (value != null) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed >= 0 && parsed <= 24) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_MAIL_LEAD_MONTHS;
    }

    @Transactional
    public int updateMailLeadMonths(int mailLeadMonths) {
        if (mailLeadMonths < 0 || mailLeadMonths > 24) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "메일 발송 기준 개월 수는 0개월 이상 24개월 이하로 설정해야 합니다.");
        }
        set(KEY_MAIL_LEAD_MONTHS, String.valueOf(mailLeadMonths));
        return mailLeadMonths;
    }

    public int getResponseDeadlineMonths() {
        String value = get(KEY_RESPONSE_DEADLINE_MONTHS);
        if (value != null) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed >= 0) return parsed;
            } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_RESPONSE_DEADLINE_MONTHS;
    }

    public int getResponseDeadlineDays() {
        String value = get(KEY_RESPONSE_DEADLINE_DAYS);
        if (value != null) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed >= 0) return parsed;
            } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_RESPONSE_DEADLINE_DAYS;
    }

    public void updateResponseDeadline(int months, int days) {
        if (months < 0 || days < 0) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "개월과 일은 0 이상이어야 합니다.");
        }
        if (months == 0 && days == 0) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "회신 기한은 1일 이상이어야 합니다.");
        }
        if (months > 12) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "회신 기한은 12개월 이하로 설정해야 합니다.");
        }
        set(KEY_RESPONSE_DEADLINE_MONTHS, String.valueOf(months));
        set(KEY_RESPONSE_DEADLINE_DAYS, String.valueOf(days));
    }

    @Transactional(readOnly = true)
    public List<CountryExtensionResponse> getCountryExtensions() {
        return SUPPORTED_COUNTRIES.stream()
                .map(c -> new CountryExtensionResponse(c, COUNTRY_LABELS.getOrDefault(c, c), getCountryExtensionMonths(c)))
                .toList();
    }

    public int getCountryExtensionMonths(String country) {
        String value = get(KEY_COUNTRY_EXT_PREFIX + country.toUpperCase());
        if (value != null) {
            try { return Integer.parseInt(value); } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_EXTENSION_MONTHS;
    }

    @Transactional
    public CountryExtensionResponse updateCountryExtension(String country, CountryExtensionRequest request) {
        String upperCountry = country.toUpperCase();
        set(KEY_COUNTRY_EXT_PREFIX + upperCountry, String.valueOf(request.extensionMonths()));
        return new CountryExtensionResponse(upperCountry, COUNTRY_LABELS.getOrDefault(upperCountry, upperCountry), request.extensionMonths());
    }

    /**
     * @relatedFR FR-LEGAL-24
     * FEE-06: 국가별 연차료 규칙 오버라이드(기산일 종류). 미설정 시 null을 돌려 호출부의
     * 국가별 기본 규칙(KR·US=등록일, 그 외=출원일)을 따른다.
     */
    public String getCountryFeeBasisOverride(String country) {
        String value = get(KEY_FEE_RULE_PREFIX + country.toUpperCase() + ".basis");
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    /** FEE-06: 설정등록 시 일괄 납부 연차 수 오버라이드. 미설정/파싱 실패 시 null. */
    public Integer getCountryFeeInitialLumpYearsOverride(String country) {
        return parseIntOrNull(get(KEY_FEE_RULE_PREFIX + country.toUpperCase() + ".initial_lump_years"));
    }

    /** FEE-06: 납부 주기(개월) 오버라이드. 미설정/파싱 실패 시 null. */
    public Integer getCountryFeeCycleMonthsOverride(String country) {
        return parseIntOrNull(get(KEY_FEE_RULE_PREFIX + country.toUpperCase() + ".cycle_months"));
    }

    /** I4: 설정 화면용 — 지원 국가 코드·라벨 목록(연차료 규칙 편집 대상). */
    public java.util.List<String> getSupportedCountries() {
        return SUPPORTED_COUNTRIES;
    }

    public String getCountryLabel(String country) {
        return COUNTRY_LABELS.getOrDefault(country, country);
    }

    /**
     * @relatedFR FR-LEGAL-24
     * I4: 국가별 연차료 규칙 오버라이드 저장. null 필드는 변경하지 않는다.
     */
    @Transactional
    public void updateCountryFeeRule(String country, String basis, Integer initialLumpYears, Integer cycleMonths) {
        String upper = country.toUpperCase();
        if (basis != null) {
            set(KEY_FEE_RULE_PREFIX + upper + ".basis", basis);
        }
        if (initialLumpYears != null) {
            set(KEY_FEE_RULE_PREFIX + upper + ".initial_lump_years", String.valueOf(initialLumpYears));
        }
        if (cycleMonths != null) {
            set(KEY_FEE_RULE_PREFIX + upper + ".cycle_months", String.valueOf(cycleMonths));
        }
    }

    /**
     * F2: 국가별 연차료 요금표 오버라이드(fee.amounts.{CC}).
     * 형식 "시작연차-끝연차:금액,..."(연차 기반) 또는 "개월:금액,..."(US 유지료). 미설정 시 null.
     */
    public String getCountryFeeAmountTableOverride(String country) {
        String value = get(KEY_FEE_RULE_PREFIX.replace("rule", "amounts") + country.toUpperCase());
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<ClassificationResponse> getClassifications() {
        return List.of(
                new ClassificationResponse("BUSINESS", getClassificationValues("BUSINESS")),
                new ClassificationResponse("TECHNOLOGY", getClassificationValues("TECHNOLOGY")));
    }

    @Transactional
    public ClassificationResponse addClassification(String type, String value) {
        String normalizedType = normalizeClassificationType(type);
        String normalizedValue = normalizeClassificationValue(value);
        List<String> values = new java.util.ArrayList<>(getClassificationValuesForUpdate(normalizedType));
        if (!values.contains(normalizedValue)) {
            values.add(normalizedValue);
        }
        values = normalizedClassificationValues(values);
        setForUpdate(classificationKey(normalizedType), writeClassificationValues(values));
        return new ClassificationResponse(normalizedType, values);
    }

    @Transactional
    public ClassificationResponse renameClassification(String type, String currentValue, String nextValue) {
        String normalizedType = normalizeClassificationType(type);
        String normalizedCurrent = normalizeClassificationValue(currentValue);
        String normalizedNext = normalizeClassificationValue(nextValue);
        List<String> values = new java.util.ArrayList<>(getClassificationValuesForUpdate(normalizedType));
        int index = values.indexOf(normalizedCurrent);
        if (index >= 0) {
            values.set(index, normalizedNext);
        } else {
            values.add(normalizedNext);
        }
        values = normalizedClassificationValues(values);
        setForUpdate(classificationKey(normalizedType), writeClassificationValues(values));
        return new ClassificationResponse(normalizedType, values);
    }

    @Transactional
    public ClassificationResponse deleteClassification(String type, String value) {
        String normalizedType = normalizeClassificationType(type);
        String normalizedValue = normalizeClassificationValue(value);
        List<String> values = getClassificationValuesForUpdate(normalizedType).stream()
                .filter(item -> !item.equals(normalizedValue))
                .toList();
        values = normalizedClassificationValues(values);
        setForUpdate(classificationKey(normalizedType), writeClassificationValues(values));
        return new ClassificationResponse(normalizedType, values);
    }

    private List<String> getClassificationValues(String type) {
        String normalizedType = normalizeClassificationType(type);
        String savedValue = get(classificationKey(normalizedType));
        if (savedValue == null || savedValue.isBlank()) {
            return defaultClassificationValues(normalizedType);
        }
        return readClassificationValues(savedValue);
    }

    private List<String> getClassificationValuesForUpdate(String type) {
        String normalizedType = normalizeClassificationType(type);
        String savedValue = repository.findByIdForUpdate(classificationKey(normalizedType))
                .map(SystemSettingsEntity::getValue)
                .orElse(null);
        if (savedValue == null || savedValue.isBlank()) {
            return defaultClassificationValues(normalizedType);
        }
        return readClassificationValues(savedValue);
    }

    private List<String> readClassificationValues(String savedValue) {
        if (savedValue.trim().startsWith("[")) {
            try {
                return normalizedClassificationValues(OBJECT_MAPPER.readValue(savedValue, STRING_LIST));
            } catch (JsonProcessingException ignored) {
                return List.of();
            }
        }
        return normalizedClassificationValues(Stream.of(savedValue.split("\\n"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList());
    }

    private List<String> normalizedClassificationValues(List<String> values) {
        return values.stream()
                .filter(value -> value != null)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> defaultClassificationValues(String type) {
        return "TECHNOLOGY".equals(type) ? DEFAULT_TECHNOLOGY_CLASSIFICATIONS : DEFAULT_BUSINESS_CLASSIFICATIONS;
    }

    private String normalizeClassificationType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase();
        if (!"BUSINESS".equals(normalized) && !"TECHNOLOGY".equals(normalized)) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "분류 타입은 BUSINESS 또는 TECHNOLOGY만 사용할 수 있습니다.");
        }
        return normalized;
    }

    private String normalizeClassificationValue(String value) {
        if (value == null || value.isBlank()) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "분류명은 비워둘 수 없습니다.");
        }
        return value.trim();
    }

    private String classificationKey(String type) {
        return KEY_CLASSIFICATION_PREFIX + type.toLowerCase();
    }

    private String writeClassificationValues(List<String> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(normalizedClassificationValues(values));
        } catch (JsonProcessingException e) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "분류 설정을 저장할 수 없습니다.");
        }
    }

    private void setForUpdate(String key, String value) {
        SystemSettingsEntity entity = repository.findByIdForUpdate(key)
                .orElse(new SystemSettingsEntity(key));
        entity.setValue(value);
        repository.save(entity);
    }

}
