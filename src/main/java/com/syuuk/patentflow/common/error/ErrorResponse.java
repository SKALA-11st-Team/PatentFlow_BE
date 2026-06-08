package com.syuuk.patentflow.common.error;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

public record ErrorResponse(
        String code,
        String message,
        Map<String, Object> details,
        OffsetDateTime timestamp
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.message(), Map.of(), now());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        String resolvedMessage = message == null || message.isBlank() ? errorCode.message() : message;
        return new ErrorResponse(errorCode.name(), resolvedMessage, Map.of(), now());
    }

    public static ErrorResponse of(ErrorCode errorCode, Map<String, Object> details) {
        return new ErrorResponse(errorCode.name(), errorCode.message(), details, now());
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
