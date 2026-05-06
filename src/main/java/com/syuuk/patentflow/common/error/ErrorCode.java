package com.syuuk.patentflow.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    PATENT_NOT_FOUND(HttpStatus.NOT_FOUND, "특허 정보를 찾을 수 없습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값을 확인해주세요."),
    INVALID_WORKFLOW_STATUS(HttpStatus.CONFLICT, "현재 workflow 단계에서 수행할 수 없는 요청입니다."),
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
