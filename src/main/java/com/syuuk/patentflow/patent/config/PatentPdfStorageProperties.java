/**
 * @author 유건욱
 * @date 2026-06-11
 */
package com.syuuk.patentflow.patent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @relatedFR FR-LEGAL-13, FR-LEGAL-14
 * MAIL-12: 특허 공개전문 PDF S3 캐시 설정. enabled=false 또는 bucket 미설정이면
 * PDF 링크 해석이 전부 원문 URL 폴백으로 동작한다(기능 플래그 겸용).
 */
@ConfigurationProperties(prefix = "patentflow.pdf-storage")
public record PatentPdfStorageProperties(
        boolean enabled,
        String bucket,
        String region,
        String keyPrefix,
        int presignTtlDays
) {

    public boolean usable() {
        return enabled && bucket != null && !bucket.isBlank();
    }
}
