package com.syuuk.patentflow.mailing.dto;

import jakarta.validation.constraints.NotBlank;

public record BusinessReviewMailPatentSummary(
        @NotBlank String patentId,
        String managementNumber,
        String originalPatentUrl,
        String title,
        // MAIL-12: PDF 다운로드 링크(선택). 원문 URL과 별개로 이력에 보존한다 — presigned URL이라 만료될 수 있다.
        String pdfDownloadUrl
) {
}
