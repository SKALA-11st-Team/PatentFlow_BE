package com.syuuk.patentflow.patent.dto;

public record PatchFinalDecisionRequest(
        LegalActionResult legalActionResult,
        String reason
) {
}
