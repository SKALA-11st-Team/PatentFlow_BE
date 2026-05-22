package com.syuuk.patentflow.common.service;

import com.syuuk.patentflow.common.domain.SystemSettingsEntity;
import com.syuuk.patentflow.common.dto.ClassificationResponse;
import com.syuuk.patentflow.common.dto.CountryExtensionRequest;
import com.syuuk.patentflow.common.dto.CountryExtensionResponse;
import com.syuuk.patentflow.common.dto.MailSettingsRequest;
import com.syuuk.patentflow.common.dto.MailSettingsResponse;
import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.repository.SystemSettingsRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SystemSettingsService {

    static final String KEY_GMAIL_USERNAME = "mail.gmail.username";
    static final String KEY_GMAIL_APP_PASSWORD = "mail.gmail.app_password";
    static final String KEY_MAIL_LEAD_MONTHS = "review.mail.lead_months";
    private static final String KEY_COUNTRY_EXT_PREFIX = "country.extension.";
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
    private static final List<String> DEFAULT_BUSINESS_CLASSIFICATIONS = List.of(
            "AI", "Data", "Blockchain", "Cloud", "ESG", "제조", "통신", "금융/전략", "통합서비스", "기존 사업");
    private static final List<String> DEFAULT_TECHNOLOGY_CLASSIFICATIONS = List.of(
            "데이터분석", "AB Testing", "Blockchain", "Cloud", "시스템 운영", "장애관리", "Green IT (AMR)",
            "인증", "보안", "Network", "NFC", "AR", "VR (Avatar)", "사내시스템");

    private final SystemSettingsRepository repository;

    public SystemSettingsService(SystemSettingsRepository repository) {
        this.repository = repository;
    }

    public String get(String key) {
        return repository.findById(key).map(SystemSettingsEntity::getValue).orElse(null);
    }

    public void set(String key, String value) {
        SystemSettingsEntity entity = repository.findById(key).orElse(new SystemSettingsEntity(key));
        entity.setValue(value);
        repository.save(entity);
    }

    public String getGmailUsername() {
        return get(KEY_GMAIL_USERNAME);
    }

    public String getGmailAppPassword() {
        return get(KEY_GMAIL_APP_PASSWORD);
    }

    public MailSettingsResponse getMailSettings() {
        String username = getGmailUsername();
        String password = getGmailAppPassword();
        return new MailSettingsResponse(username, password != null && !password.isBlank());
    }

    public MailSettingsResponse saveMailSettings(MailSettingsRequest request) {
        if (request.gmailUsername() != null) {
            set(KEY_GMAIL_USERNAME, request.gmailUsername().trim());
        }
        if (request.gmailAppPassword() != null && !request.gmailAppPassword().isBlank()) {
            set(KEY_GMAIL_APP_PASSWORD, request.gmailAppPassword().trim());
        }
        return getMailSettings();
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

    public int updateMailLeadMonths(int mailLeadMonths) {
        if (mailLeadMonths < 0 || mailLeadMonths > 24) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "메일 발송 기준 개월 수는 0개월 이상 24개월 이하로 설정해야 합니다.");
        }
        set(KEY_MAIL_LEAD_MONTHS, String.valueOf(mailLeadMonths));
        return mailLeadMonths;
    }

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

    public CountryExtensionResponse updateCountryExtension(String country, CountryExtensionRequest request) {
        String upperCountry = country.toUpperCase();
        set(KEY_COUNTRY_EXT_PREFIX + upperCountry, String.valueOf(request.extensionMonths()));
        return new CountryExtensionResponse(upperCountry, COUNTRY_LABELS.getOrDefault(upperCountry, upperCountry), request.extensionMonths());
    }

    public List<ClassificationResponse> getClassifications() {
        return List.of(
                new ClassificationResponse("BUSINESS", getClassificationValues("BUSINESS")),
                new ClassificationResponse("TECHNOLOGY", getClassificationValues("TECHNOLOGY")));
    }

    public ClassificationResponse addClassification(String type, String value) {
        String normalizedType = normalizeClassificationType(type);
        String normalizedValue = normalizeClassificationValue(value);
        List<String> values = new java.util.ArrayList<>(getClassificationValues(normalizedType));
        if (!values.contains(normalizedValue)) {
            values.add(normalizedValue);
            values.sort(String::compareTo);
            set(classificationKey(normalizedType), writeClassificationValues(values));
        }
        return new ClassificationResponse(normalizedType, values);
    }

    public ClassificationResponse renameClassification(String type, String currentValue, String nextValue) {
        String normalizedType = normalizeClassificationType(type);
        String normalizedCurrent = normalizeClassificationValue(currentValue);
        String normalizedNext = normalizeClassificationValue(nextValue);
        List<String> values = new java.util.ArrayList<>(getClassificationValues(normalizedType));
        int index = values.indexOf(normalizedCurrent);
        if (index >= 0) {
            values.set(index, normalizedNext);
        } else {
            values.add(normalizedNext);
        }
        values = values.stream().distinct().sorted().toList();
        set(classificationKey(normalizedType), writeClassificationValues(values));
        return new ClassificationResponse(normalizedType, values);
    }

    public ClassificationResponse deleteClassification(String type, String value) {
        String normalizedType = normalizeClassificationType(type);
        String normalizedValue = normalizeClassificationValue(value);
        List<String> values = getClassificationValues(normalizedType).stream()
                .filter(item -> !item.equals(normalizedValue))
                .sorted()
                .toList();
        set(classificationKey(normalizedType), writeClassificationValues(values));
        return new ClassificationResponse(normalizedType, values);
    }

    private List<String> getClassificationValues(String type) {
        String normalizedType = normalizeClassificationType(type);
        String savedValue = get(classificationKey(normalizedType));
        if (savedValue == null || savedValue.isBlank()) {
            return defaultClassificationValues(normalizedType);
        }
        return java.util.Arrays.stream(savedValue.split("\\n"))
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
        return String.join("\n", values);
    }
}
