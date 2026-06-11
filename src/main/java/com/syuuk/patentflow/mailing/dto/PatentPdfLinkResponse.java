package com.syuuk.patentflow.mailing.dto;

import java.time.OffsetDateTime;

/**
 * @relatedFR FR-LEGAL-13
 * MAIL-12/MAIL-13: 특허별 PDF 다운로드 링크 해석 결과.
 * source=UPLOADED — 법무팀 업로드본이 시스템에 있음. pdfUrl은 null이며 FE가 특허 상세
 *   딥링크(로그인 후 다운로드)를 안내한다(TW·UAE 등 KIPRIS 미지원 국가 대응).
 * source=KIPRIS_S3 — pdfUrl은 S3 presigned 다운로드 링크(expiresAt까지 유효).
 * source=ORIGINAL_URL — 기존 특허 원문 URL 폴백(비KR·미공개·저장소 비활성 등).
 */
public record PatentPdfLinkResponse(
        String patentId,
        String pdfUrl,
        String source,
        OffsetDateTime expiresAt
) {

    public static final String SOURCE_UPLOADED = "UPLOADED";
    public static final String SOURCE_KIPRIS_S3 = "KIPRIS_S3";
    public static final String SOURCE_ORIGINAL_URL = "ORIGINAL_URL";
}
