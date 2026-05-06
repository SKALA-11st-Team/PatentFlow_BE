package com.syuuk.patentflow.common.response;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record ApiResponse<T>(
        T data,
        String message,
        OffsetDateTime timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, "OK", OffsetDateTime.now(ZoneId.of("Asia/Seoul")));
    }
}
