package com.syuuk.patentflow.common.service;

import com.syuuk.patentflow.common.domain.SystemSettingsEntity;
import com.syuuk.patentflow.common.dto.CountryExtensionRequest;
import com.syuuk.patentflow.common.dto.CountryExtensionResponse;
import com.syuuk.patentflow.common.dto.MailSettingsRequest;
import com.syuuk.patentflow.common.dto.MailSettingsResponse;
import com.syuuk.patentflow.common.repository.SystemSettingsRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SystemSettingsService {

    static final String KEY_GMAIL_USERNAME = "mail.gmail.username";
    static final String KEY_GMAIL_APP_PASSWORD = "mail.gmail.app_password";
    private static final String KEY_COUNTRY_EXT_PREFIX = "country.extension.";

    private static final List<String> SUPPORTED_COUNTRIES = List.of("KR", "JP", "CN", "US", "TW");
    private static final Map<String, String> COUNTRY_LABELS = Map.of(
            "KR", "한국 (KR)",
            "JP", "일본 (JP)",
            "CN", "중국 (CN)",
            "US", "미국 (US)",
            "TW", "대만 (TW)"
    );
    private static final int DEFAULT_EXTENSION_MONTHS = 12;

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
}
