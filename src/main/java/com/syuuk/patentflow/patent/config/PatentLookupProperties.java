package com.syuuk.patentflow.patent.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "patentflow.lookup")
public record PatentLookupProperties(
        Kipris kipris,
        GooglePatents googlePatents
) {

    public record Kipris(
            String baseUrl,
            String servicePath,
            String bibliographyOperation,
            String applicationSearchOperation,
            String pdfPathOperation,
            // W4: 국가별 공개전문 PDF 오퍼레이션(예: US/JP/CN 해외특허 API명). 미설정 국가는 PDF 미지원.
            // KR은 pdfPathOperation(기본 getPubFullTextInfoSearch)을 사용한다.
            java.util.Map<String, String> pdfPathOperations,
            String serviceKey,
            List<String> serviceKeys
    ) {

        /** W4: 국가별 PDF 오퍼레이션 — KR은 기본 오퍼레이션, 그 외는 명시 설정된 국가만 지원. */
        public String pdfOperationFor(String country) {
            String normalized = country == null ? "" : country.trim().toUpperCase();
            if (pdfPathOperations != null && pdfPathOperations.containsKey(normalized)) {
                String operation = pdfPathOperations.get(normalized);
                return operation == null || operation.isBlank() ? null : operation.trim();
            }
            if ("KR".equals(normalized)) {
                return pdfPathOperation == null || pdfPathOperation.isBlank() ? null : pdfPathOperation.trim();
            }
            return null;
        }
    }

    public record GooglePatents(
            boolean enabled,
            String baseUrl
    ) {
    }
}
