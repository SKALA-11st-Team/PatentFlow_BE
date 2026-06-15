package com.syuuk.patentflow.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    PATENT_NOT_FOUND(HttpStatus.NOT_FOUND, "특허 정보를 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자 정보를 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."),
    LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인 실패가 반복되어 잠시 후 다시 시도해주세요."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값을 확인해주세요."),
    INVALID_WORKFLOW_STATUS(HttpStatus.CONFLICT, "현재 workflow 단계에서 수행할 수 없는 요청입니다."),
    CO_APPLICANT_CONSENT_REQUIRED(HttpStatus.CONFLICT, "공동출원 특허입니다. 연차료 유지/포기 결정 전 공동출원인 합의가 필요합니다."),
    NOT_A_JOINT_APPLICATION(HttpStatus.BAD_REQUEST, "공동출원 특허가 아니어서 공동출원인 합의를 기록할 수 없습니다."),
    AI_REPORT_EDIT_CONFLICT(HttpStatus.CONFLICT, "AI 레포트가 다른 곳에서 변경되었습니다. 새로고침 후 다시 시도해주세요."),
    MAIL_NOT_CONFIGURED(HttpStatus.BAD_REQUEST, "Gmail 메일 발송 설정이 완료되지 않았습니다. 설정 페이지에서 Gmail 계정과 앱 비밀번호를 등록해 주세요."),
    MAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "메일 발송에 실패했습니다. Gmail 설정을 확인해 주세요."),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "보안 토큰이 유효하지 않습니다. 잠시 후 다시 시도해 주세요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
