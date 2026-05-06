package com.syuuk.patentflow.business.dto;

public record BusinessChecklistResponseDto(
        String itemId,
        Integer score,
        int aiSuggestedScore,
        String memo
) {
}
