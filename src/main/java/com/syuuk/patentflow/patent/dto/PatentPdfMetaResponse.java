/**
 * @author 유건욱
 * @date 2026-06-11
 */
package com.syuuk.patentflow.patent.dto;

import java.time.OffsetDateTime;

/**
 * @relatedFR FR-LEGAL-13
 * MAIL-13: 특허 PDF 첨부 상태 — 등록/수정 화면의 업로드 위젯과 상세 화면의 다운로드 버튼이 사용한다.
 */
public record PatentPdfMetaResponse(
        String patentId,
        boolean exists,
        String storageType,
        String docName,
        Long contentLength,
        String uploadedBy,
        OffsetDateTime createdAt
) {

    public static PatentPdfMetaResponse none(String patentId) {
        return new PatentPdfMetaResponse(patentId, false, null, null, null, null, null);
    }
}
