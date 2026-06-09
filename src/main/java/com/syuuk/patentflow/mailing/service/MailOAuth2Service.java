package com.syuuk.patentflow.mailing.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.service.SystemSettingsService;
import com.syuuk.patentflow.mailing.config.MailOAuth2Properties;
import com.syuuk.patentflow.mailing.dto.MailOAuth2StatusResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class MailOAuth2Service {

    private static final Logger log = LoggerFactory.getLogger(MailOAuth2Service.class);
    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
    // Gmail 발송 + 이메일 주소 조회 (연동된 계정 표시용)
    private static final String GMAIL_SCOPE = "https://mail.google.com/ email";

    // SEC-04/MAIL-07: OAuth2 CSRF 방어 state 유효시간(authorize→callback 왕복).
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MailOAuth2Properties properties;
    private final SystemSettingsService systemSettingsService;
    private final RestTemplate restTemplate;
    private final OAuthStateStore stateStore;

    // access_token은 1시간 만료 — 55분 캐시해서 매 발송마다 토큰 교환 API 호출을 방지
    private String cachedAccessToken;
    private Instant cachedTokenExpiry;

    public MailOAuth2Service(MailOAuth2Properties properties,
            SystemSettingsService systemSettingsService,
            RestTemplateBuilder restTemplateBuilder,
            OAuthStateStore stateStore) {
        this.properties = properties;
        this.systemSettingsService = systemSettingsService;
        this.stateStore = stateStore;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }

    // ── 인증 URL ──────────────────────────────────────────────

    public String buildAuthorizationUrl() {
        String clientId = systemSettingsService.getGmailOAuth2ClientId();
        if (clientId.isBlank()) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "Google OAuth2 Client ID가 설정되지 않았습니다. 설정 페이지에서 Client ID를 먼저 입력하세요.");
        }
        // SEC-04/MAIL-07: CSRF 방어용 난수 state를 발급·저장하고 URL에 실어, 콜백에서 단발 검증한다.
        String state = generateState();
        stateStore.save(state, STATE_TTL);
        return UriComponentsBuilder.fromUriString(GOOGLE_AUTH_URL)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", GMAIL_SCOPE)
                // offline → refresh_token 발급, consent → 재연동 시에도 항상 refresh_token 재발급
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build().toUriString();
    }

    /** 콜백의 state를 단발 검증한다(발급한 state와 일치하고 미만료·미사용일 때만 true). */
    public boolean validateState(String state) {
        return state != null && !state.isBlank() && stateStore.consume(state);
    }

    private static String generateState() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ── 코드 교환 및 저장 ─────────────────────────────────────

    public void exchangeCodeAndSave(String code) {
        Map<String, Object> tokenResponse = requestTokens(code);

        String refreshToken = (String) tokenResponse.get("refresh_token");
        String accessToken = (String) tokenResponse.get("access_token");
        if (refreshToken == null || refreshToken.isBlank()) {
            // prompt=consent를 설정했는데도 refresh_token이 없으면 이미 연동된 계정
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "refresh_token을 받지 못했습니다. Google Cloud Console에서 앱이 'Testing' 상태이거나 이미 연동된 계정일 수 있습니다.");
        }

        String email = fetchUserEmail(accessToken);
        systemSettingsService.saveGmailOAuth2(refreshToken, email);
        // 방금 받은 access_token을 즉시 캐시해 첫 발송 시 불필요한 갱신 호출을 줄인다.
        cacheAccessToken(accessToken, tokenResponse);
        log.info("Gmail OAuth2 connected: {}", email);
    }

    // ── access_token 조회 (캐시 우선) ────────────────────────

    public String getValidAccessToken() {
        if (cachedAccessToken != null && Instant.now().isBefore(cachedTokenExpiry)) {
            return cachedAccessToken;
        }
        return refreshAccessToken();
    }

    // ── 연동 상태 ─────────────────────────────────────────────

    public MailOAuth2StatusResponse getStatus() {
        String email = systemSettingsService.getGmailOAuth2ConnectedEmail();
        boolean connected = systemSettingsService.getGmailOAuth2RefreshToken() != null;
        return new MailOAuth2StatusResponse(connected, connected ? email : null);
    }

    public boolean isConnected() {
        return systemSettingsService.getGmailOAuth2RefreshToken() != null;
    }

    public void disconnect() {
        cachedAccessToken = null;
        cachedTokenExpiry = null;
        systemSettingsService.clearGmailOAuth2();
        log.info("Gmail OAuth2 disconnected.");
    }

    // ── 내부 유틸 ─────────────────────────────────────────────

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private Map<String, Object> requestTokens(String code) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", systemSettingsService.getGmailOAuth2ClientId());
        params.add("client_secret", systemSettingsService.getGmailOAuth2ClientSecret());
        params.add("redirect_uri", properties.getRedirectUri());
        params.add("grant_type", "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    GOOGLE_TOKEN_URL, HttpMethod.POST,
                    new HttpEntity<>(params, headers), MAP_TYPE);
            return response.getBody();
        } catch (Exception e) {
            log.error("Google token exchange failed: {}", e.getMessage());
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "Google 토큰 교환에 실패했습니다: " + e.getMessage());
        }
    }

    private String refreshAccessToken() {
        String refreshToken = systemSettingsService.getGmailOAuth2RefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST,
                    "Gmail OAuth2가 연동되지 않았습니다. 설정 페이지에서 Google 계정을 연동해 주세요.");
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("refresh_token", refreshToken);
        params.add("client_id", systemSettingsService.getGmailOAuth2ClientId());
        params.add("client_secret", systemSettingsService.getGmailOAuth2ClientSecret());
        params.add("grant_type", "refresh_token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    GOOGLE_TOKEN_URL, HttpMethod.POST,
                    new HttpEntity<>(params, headers), MAP_TYPE);
            Map<String, Object> body = response.getBody();
            String accessToken = (String) body.get("access_token");
            cacheAccessToken(accessToken, body);
            return accessToken;
        } catch (Exception e) {
            log.error("Google token refresh failed: {}", e.getMessage());
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "Gmail access_token 갱신에 실패했습니다: " + e.getMessage());
        }
    }

    private String fetchUserEmail(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    GOOGLE_USERINFO_URL, HttpMethod.GET,
                    new HttpEntity<>(headers), MAP_TYPE);
            Map<String, Object> body = response.getBody();
            return body != null ? (String) body.get("email") : "";
        } catch (Exception e) {
            log.warn("Failed to fetch Google user email: {}", e.getMessage());
            return "";
        }
    }

    private void cacheAccessToken(String accessToken, Map<String, Object> tokenResponse) {
        Object expiresIn = tokenResponse.get("expires_in");
        int secondsUntilExpiry = expiresIn instanceof Number ? ((Number) expiresIn).intValue() : 3600;
        this.cachedAccessToken = accessToken;
        // 만료 5분 전에 갱신하도록 여유 시간을 둔다
        this.cachedTokenExpiry = Instant.now().plusSeconds(secondsUntilExpiry - 300);
    }
}
