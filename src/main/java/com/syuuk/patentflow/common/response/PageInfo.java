package com.syuuk.patentflow.common.response;

public record PageInfo(
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
