package com.syuuk.patentflow.common.response;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record PageResponse<T>(
        List<T> data,
        PageInfo page,
        String message,
        OffsetDateTime timestamp
) {

    public static <T> PageResponse<T> ok(List<T> data, PageInfo page) {
        return new PageResponse<>(data, page, "OK", OffsetDateTime.now(ZoneId.of("Asia/Seoul")));
    }
}
