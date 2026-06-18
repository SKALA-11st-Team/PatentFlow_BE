/**
 * @author 이소율
 * @date 2026-05-28
 */
package com.syuuk.patentflow.mailing.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.mailing.config.MailOAuth2Properties;
import com.syuuk.patentflow.mailing.dto.MailOAuth2StatusResponse;
import com.syuuk.patentflow.mailing.service.MailOAuth2Service;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @relatedFR FR-LEGAL-13
 * @relatedUI UI-LEGAL-05
 * @description 사업부 검토 요청 메일 발송을 위한 Google 계정 연동(상태 조회/인증 URL 발급/콜백/연동 해제) API.
 */
@RestController
@RequestMapping("/api/v1/admin/settings/mail/oauth2/google")
public class MailOAuth2Controller {

    private final MailOAuth2Service mailOAuth2Service;
    private final MailOAuth2Properties properties;

    public MailOAuth2Controller(MailOAuth2Service mailOAuth2Service, MailOAuth2Properties properties) {
        this.mailOAuth2Service = mailOAuth2Service;
        this.properties = properties;
    }

    // ── 연동 상태 조회 ────────────────────────────────────────

    /**
     * @relatedFR FR-LEGAL-13
     * @relatedUI UI-LEGAL-05
     * @description 현재 Google 계정 연동 여부와 연동된 발신 이메일 주소를 조회한다.
     */
    @GetMapping("/status")
    public ApiResponse<MailOAuth2StatusResponse> getStatus() {
        return ApiResponse.ok(mailOAuth2Service.getStatus());
    }

    // ── 인증 URL 반환 ─────────────────────────────────────────
    // 브라우저가 직접 이동하면 JWT 헤더가 없어 401이 발생하므로
    // FE가 JWT 인증으로 URL을 받아서 window.location.href로 이동한다.

    /**
     * @relatedFR FR-LEGAL-13
     * @relatedUI UI-LEGAL-05
     * @description Google 계정 연동을 시작할 인증 URL을 발급한다(FE가 JWT 인증으로 받아 직접 이동).
     */
    @GetMapping("/authorize-url")
    public ApiResponse<String> getAuthorizeUrl() {
        return ApiResponse.ok(mailOAuth2Service.buildAuthorizationUrl());
    }

    // ── OAuth 콜백 ────────────────────────────────────────────
    // 성공: refresh_token 저장 후 FE 설정 페이지로 리다이렉트
    // 실패: error 파라미터를 포함해 FE로 리다이렉트

    /**
     * @relatedFR FR-LEGAL-13
     * @relatedUI UI-LEGAL-05
     * @description Google 계정 연동 콜백을 처리한다. state 검증 후 code를 교환해 refresh_token을 저장하고
     *     성공/실패 결과를 FE 설정 페이지로 리다이렉트한다.
     */
    @GetMapping("/callback")
    public void callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String state,
            HttpServletResponse response) throws IOException {
        String frontendUri = properties.getFrontendSettingsUri();
        if (error != null) {
            // SEC-11: 외부에서 온 error 원문을 무검증 반사하지 않고 URL 인코딩해 파라미터 변조(CRLF·쿼리 주입)를 차단.
            response.sendRedirect(frontendUri + "?oauth2_error=" + URLEncoder.encode(error, StandardCharsets.UTF_8));
            return;
        }
        // SEC-04/MAIL-07: 발급한 state와 일치하지 않으면(위조/재사용) code 교환 전에 거부한다.
        if (!mailOAuth2Service.validateState(state)) {
            response.sendRedirect(frontendUri + "?oauth2_error=invalid_state");
            return;
        }
        if (code == null || code.isBlank()) {
            response.sendRedirect(frontendUri + "?oauth2_error=no_code");
            return;
        }
        try {
            mailOAuth2Service.exchangeCodeAndSave(code);
            response.sendRedirect(frontendUri + "?oauth2_success=true");
        } catch (Exception e) {
            response.sendRedirect(frontendUri + "?oauth2_error=exchange_failed");
        }
    }

    // ── 연동 해제 ─────────────────────────────────────────────

    /**
     * @relatedFR FR-LEGAL-13
     * @relatedUI UI-LEGAL-05
     * @description 저장된 Google 계정 연동 정보를 삭제해 메일 발송 연동을 해제한다.
     */
    @DeleteMapping
    public ApiResponse<MailOAuth2StatusResponse> disconnect() {
        mailOAuth2Service.disconnect();
        return ApiResponse.ok(new MailOAuth2StatusResponse(false, null));
    }
}
