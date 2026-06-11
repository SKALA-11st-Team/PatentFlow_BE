package com.syuuk.patentflow.patent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * @relatedFR FR-LEGAL-13
 * MAIL-12: PDF 캐시용 S3 클라이언트. 자격증명은 DefaultCredentialsProvider 체인을 따른다
 * (EKS IRSA 또는 AWS_ACCESS_KEY_ID/SECRET 환경변수 — 배포 가이드 참조).
 * 주의: presigned URL 유효기간은 서명 자격증명 수명에 종속된다. IRSA 임시 토큰으로 서명하면
 * 설정 TTL(기본 7일) 전에 URL이 만료될 수 있으므로 장기 IAM 키 사용을 권장한다.
 */
@Configuration
@ConditionalOnProperty(prefix = "patentflow.pdf-storage", name = "enabled", havingValue = "true")
public class PatentPdfStorageS3Config {

    @Bean
    public S3Client patentPdfS3Client(PatentPdfStorageProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .build();
    }

    @Bean
    public S3Presigner patentPdfS3Presigner(PatentPdfStorageProperties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .build();
    }
}
