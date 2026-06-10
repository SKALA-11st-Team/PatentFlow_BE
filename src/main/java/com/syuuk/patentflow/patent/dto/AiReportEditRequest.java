package com.syuuk.patentflow.patent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * AI 레포트 법무 편집 요청(계약 C2 — PATCH /api/v1/patents/{patentId}/ai-report).
 *
 * @param baseReportId 편집 화면이 기준으로 삼은 레포트 ID. 현재 저장된 ai_report_id와 다르면
 *                     편집 도중 레포트가 재생성된 것이므로 409로 거절한다.
 * @param expectedEditVersion 편집 화면이 로드한 시점의 편집 버전(최초 편집은 0). 불일치 시 409(동시 편집).
 * @param overrides 수정할 필드만 담은 부분 오버라이드.
 */
public record AiReportEditRequest(
        @NotBlank String baseReportId,
        @NotNull Integer expectedEditVersion,
        @NotNull AiReportOverrides overrides
) {
}
