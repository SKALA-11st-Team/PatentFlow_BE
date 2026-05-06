package com.syuuk.patentflow.patent.dto;

public record PatentContextSuggestionResponse(
        String businessArea,
        String confidenceText,
        String reason,
        String technologyArea
) {
}
