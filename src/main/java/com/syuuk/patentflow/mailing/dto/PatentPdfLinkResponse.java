package com.syuuk.patentflow.mailing.dto;

import java.time.OffsetDateTime;

/**
 * @relatedFR FR-LEGAL-13
 * MAIL-12: 특허별 PDF 다운로드 링크 해석 결과.
 * source=KIPRIS_S3면 pdfUrl은 S3 presigned 다운로드 링크(expiresAt까지 유효),
 * source=ORIGINAL_URL이면 기존 특허 원문 URL 폴백(비KR·미공개·저장소 비활성 등)이다.
 */
public record PatentPdfLinkResponse(
        String patentId,
        String pdfUrl,
        String source,
        OffsetDateTime expiresAt
) {

    public static final String SOURCE_KIPRIS_S3 = "KIPRIS_S3";
    public static final String SOURCE_ORIGINAL_URL = "ORIGINAL_URL";
}
