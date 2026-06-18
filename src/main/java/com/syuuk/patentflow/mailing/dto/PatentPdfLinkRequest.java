/**
 * @author 유건욱
 * @date 2026-06-11
 */
package com.syuuk.patentflow.mailing.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @relatedFR FR-LEGAL-13
 * MAIL-12: 메일 초안에 실을 특허 PDF 다운로드 링크 일괄 해석 요청.
 */
public record PatentPdfLinkRequest(
        @NotEmpty @Size(max = 100) List<String> patentIds
) {
}
