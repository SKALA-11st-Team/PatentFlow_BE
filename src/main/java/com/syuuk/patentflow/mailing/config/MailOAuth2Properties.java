package com.syuuk.patentflow.mailing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "patentflow.oauth2.google")
public class MailOAuth2Properties {

    private String clientId = "";
    private String clientSecret = "";
    // BE 콜백 URI — Google Cloud Console에 등록한 값과 일치해야 함
    private String redirectUri = "http://localhost:8080/api/v1/admin/settings/mail/oauth2/google/callback";
    // 콜백 성공 후 관리자를 돌려보낼 FE 페이지
    private String frontendSettingsUri = "http://localhost:5173/admin/settings";

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getFrontendSettingsUri() { return frontendSettingsUri; }
    public void setFrontendSettingsUri(String frontendSettingsUri) { this.frontendSettingsUri = frontendSettingsUri; }
}
