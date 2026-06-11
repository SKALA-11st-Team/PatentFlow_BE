package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.mailing.dto.PatentPdfLinkResponse;
import com.syuuk.patentflow.patent.client.KiprisPdfPathClient;
import com.syuuk.patentflow.patent.config.PatentPdfStorageProperties;
import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import com.syuuk.patentflow.patent.domain.PatentPdfDocumentEntity;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import com.syuuk.patentflow.patent.repository.PatentPdfDocumentRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * @relatedFR FR-LEGAL-13, FR-LEGAL-14
 * @relatedUI UI-LEGAL-05
 * MAIL-12: 메일에 실을 특허 PDF 다운로드 링크 해석.
 * 한국 특허는 KIPRIS 공개전문 PDF를 받아 S3에 캐시하고 presigned 링크를 만들며,
 * 비KR·미공개·저장소 비활성·다운로드 실패 등 모든 예외 상황은 기존 원문 URL로 폴백한다.
 * 어떤 경우에도 예외를 전파해 메일 초안/발송을 막지 않는다.
 */
@Service
public class PatentPdfService {

    private static final Logger log = LoggerFactory.getLogger(PatentPdfService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(15);
    private static final long MAX_PDF_BYTES = 50L * 1024 * 1024;

    private final PatentPdfStorageProperties properties;
    private final KiprisPdfPathClient kiprisPdfPathClient;
    private final PatentPdfDocumentRepository pdfDocumentRepository;
    private final PatentMetadataRepository patentMetadataRepository;
    private final ObjectProvider<S3Client> s3ClientProvider;
    private final ObjectProvider<S3Presigner> s3PresignerProvider;
    private final HttpClient httpClient;

    public PatentPdfService(
            PatentPdfStorageProperties properties,
            KiprisPdfPathClient kiprisPdfPathClient,
            PatentPdfDocumentRepository pdfDocumentRepository,
            PatentMetadataRepository patentMetadataRepository,
            ObjectProvider<S3Client> s3ClientProvider,
            ObjectProvider<S3Presigner> s3PresignerProvider
    ) {
        this.properties = properties;
        this.kiprisPdfPathClient = kiprisPdfPathClient;
        this.pdfDocumentRepository = pdfDocumentRepository;
        this.patentMetadataRepository = patentMetadataRepository;
        this.s3ClientProvider = s3ClientProvider;
        this.s3PresignerProvider = s3PresignerProvider;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(DOWNLOAD_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<PatentPdfLinkResponse> resolvePdfLinks(List<String> patentIds) {
        List<PatentPdfLinkResponse> results = new ArrayList<>();
        for (String patentId : patentIds) {
            results.add(resolvePdfLink(patentId));
        }
        return results;
    }

    private PatentPdfLinkResponse resolvePdfLink(String patentId) {
        PatentMetadataEntity patent = patentMetadataRepository.findById(patentId).orElse(null);
        if (patent == null) {
            return originalUrlFallback(patentId, null);
        }
        if (!isKoreanPatent(patent.getCountry()) || !properties.usable()) {
            return originalUrlFallback(patentId, patent);
        }
        try {
            String s3Key = cachedOrUploadedKey(patent);
            if (s3Key == null) {
                return originalUrlFallback(patentId, patent);
            }
            return presign(patentId, s3Key);
        } catch (Exception exception) {
            // MAIL-12: PDF 링크는 부가 정보 — 어떤 실패도 메일 흐름을 막지 않고 원문 URL로 폴백한다.
            log.warn("특허 PDF 링크 해석 실패 — 원문 URL로 폴백. patentId={}", patentId, exception);
            return originalUrlFallback(patentId, patent);
        }
    }

    private String cachedOrUploadedKey(PatentMetadataEntity patent) {
        Optional<PatentPdfDocumentEntity> cached = pdfDocumentRepository.findById(patent.getPatentId());
        if (cached.isPresent()) {
            return cached.get().getS3Key();
        }
        Optional<KiprisPdfPathClient.KiprisPdfPath> pdfPath =
                kiprisPdfPathClient.findPdfPath(patent.getApplicationNumber());
        if (pdfPath.isEmpty()) {
            return null;
        }
        byte[] pdfBytes = download(pdfPath.get().downloadUrl());
        if (pdfBytes == null) {
            return null;
        }
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            return null;
        }
        String s3Key = keyPrefix() + patent.getPatentId() + ".pdf";
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(s3Key)
                        .contentType("application/pdf")
                        .contentDisposition("attachment; filename=\"%s\"".formatted(pdfPath.get().docName()))
                        .build(),
                RequestBody.fromBytes(pdfBytes));
        pdfDocumentRepository.save(new PatentPdfDocumentEntity(
                patent.getPatentId(),
                s3Key,
                pdfPath.get().docName(),
                pdfPath.get().downloadUrl(),
                (long) pdfBytes.length,
                OffsetDateTime.now(KST)));
        return s3Key;
    }

    // 테스트에서 네트워크 없이 대체할 수 있도록 package 가시성으로 둔다.
    byte[] download(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .timeout(DOWNLOAD_TIMEOUT)
                    .uri(URI.create(url))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300 || body == null || body.length == 0) {
                return null;
            }
            if (body.length > MAX_PDF_BYTES) {
                log.warn("특허 PDF 크기 상한 초과({} bytes) — 폴백. url={}", body.length, url);
                return null;
            }
            // KIPRIS 경로가 오류 HTML을 돌려주는 경우가 있어 PDF 매직 바이트로 검증한다.
            if (body.length < 4 || body[0] != '%' || body[1] != 'P' || body[2] != 'D' || body[3] != 'F') {
                log.warn("특허 PDF 응답이 PDF 형식이 아님 — 폴백. url={}", url);
                return null;
            }
            return body;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception exception) {
            log.warn("특허 PDF 다운로드 실패. url={}", url, exception);
            return null;
        }
    }

    private PatentPdfLinkResponse presign(String patentId, String s3Key) {
        S3Presigner presigner = s3PresignerProvider.getIfAvailable();
        if (presigner == null) {
            return originalUrlFallback(patentId, patentMetadataRepository.findById(patentId).orElse(null));
        }
        Duration ttl = Duration.ofDays(Math.max(1, properties.presignTtlDays()));
        String url = presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(properties.bucket())
                                .key(s3Key)
                                .build())
                        .build())
                .url()
                .toString();
        return new PatentPdfLinkResponse(
                patentId, url, PatentPdfLinkResponse.SOURCE_KIPRIS_S3, OffsetDateTime.now(KST).plus(ttl));
    }

    private PatentPdfLinkResponse originalUrlFallback(String patentId, PatentMetadataEntity patent) {
        String url = patent == null
                ? null
                : PatentReviewService.originalPatentUrl(
                        patent.getCountry(), patent.getApplicationNumber(), patent.getRegistrationNumber());
        return new PatentPdfLinkResponse(patentId, url, PatentPdfLinkResponse.SOURCE_ORIGINAL_URL, null);
    }

    private String keyPrefix() {
        String prefix = properties.keyPrefix();
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private boolean isKoreanPatent(String country) {
        return country != null && "KR".equalsIgnoreCase(country.trim());
    }
}
