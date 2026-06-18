/**
 * @author 유건욱
 * @date 2026-06-11
 */
package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.mailing.dto.PatentPdfLinkResponse;
import com.syuuk.patentflow.patent.client.KiprisPdfPathClient;
import com.syuuk.patentflow.patent.config.PatentPdfStorageProperties;
import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import com.syuuk.patentflow.patent.domain.PatentPdfContentEntity;
import com.syuuk.patentflow.patent.domain.PatentPdfDocumentEntity;
import com.syuuk.patentflow.patent.dto.PatentPdfMetaResponse;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import com.syuuk.patentflow.patent.repository.PatentPdfContentRepository;
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
import org.springframework.transaction.annotation.Transactional;
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
    private final PatentPdfContentRepository pdfContentRepository;
    private final PatentMetadataRepository patentMetadataRepository;
    private final ObjectProvider<S3Client> s3ClientProvider;
    private final ObjectProvider<S3Presigner> s3PresignerProvider;
    private final HttpClient httpClient;

    public PatentPdfService(
            PatentPdfStorageProperties properties,
            KiprisPdfPathClient kiprisPdfPathClient,
            PatentPdfDocumentRepository pdfDocumentRepository,
            PatentPdfContentRepository pdfContentRepository,
            PatentMetadataRepository patentMetadataRepository,
            ObjectProvider<S3Client> s3ClientProvider,
            ObjectProvider<S3Presigner> s3PresignerProvider
    ) {
        this.properties = properties;
        this.kiprisPdfPathClient = kiprisPdfPathClient;
        this.pdfDocumentRepository = pdfDocumentRepository;
        this.pdfContentRepository = pdfContentRepository;
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
        // MAIL-13: 법무팀 업로드본이 있으면 국가·S3 설정과 무관하게 최우선(TW·UAE 등 KIPRIS 미지원 국가).
        // pdfUrl은 null — presigned가 아니라 인증이 필요한 앱 내 다운로드라 FE가 상세 딥링크를 안내한다.
        Optional<PatentPdfDocumentEntity> existing = pdfDocumentRepository.findById(patentId);
        if (existing.isPresent() && existing.get().isUploaded()) {
            // I6: S3에 저장된 업로드본은 presigned 링크를 직접 발급한다(메일에서 바로 다운로드).
            if (existing.get().getS3Key() != null && properties.usable()) {
                try {
                    PatentPdfLinkResponse presigned = presign(patentId, existing.get().getS3Key());
                    return new PatentPdfLinkResponse(
                            patentId, presigned.pdfUrl(), PatentPdfLinkResponse.SOURCE_UPLOADED, presigned.expiresAt());
                } catch (Exception exception) {
                    log.warn("업로드 PDF presign 실패 — 앱 내 다운로드 안내로 폴백. patentId={}", patentId, exception);
                }
            }
            return new PatentPdfLinkResponse(patentId, null, PatentPdfLinkResponse.SOURCE_UPLOADED, null);
        }
        // W4: KIPRIS PDF 오퍼레이션이 설정된 국가(KR 기본, US/JP/CN은 설정 시)만 시도한다.
        if (!properties.usable()) {
            return originalUrlFallback(patentId, patent);
        }
        try {
            String s3Key = cachedOrUploadedKey(patent, existing.orElse(null));
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

    private String cachedOrUploadedKey(PatentMetadataEntity patent, PatentPdfDocumentEntity cachedOrNull) {
        if (cachedOrNull != null) {
            return cachedOrNull.getS3Key();
        }
        Optional<KiprisPdfPathClient.KiprisPdfPath> pdfPath =
                kiprisPdfPathClient.findPdfPath(patent.getCountry(), patent.getApplicationNumber());
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
        pdfDocumentRepository.save(PatentPdfDocumentEntity.kiprisS3Cache(
                patent.getPatentId(),
                s3Key,
                pdfPath.get().docName(),
                pdfPath.get().downloadUrl(),
                (long) pdfBytes.length,
                OffsetDateTime.now(KST)));
        return s3Key;
    }

    /**
     * @relatedFR FR-LEGAL-13
     * MAIL-13: 법무팀 특허 PDF 직접 업로드 — TW·UAE 등 KIPRIS로 PDF를 가져올 수 없는 국가 대응.
     * 기존 첨부(업로드본/KIPRIS 캐시)는 교체된다. PDF 매직 바이트·크기 상한을 검증한다.
     */
    @Transactional
    public PatentPdfMetaResponse upload(String patentId, String docName, byte[] content, String uploadedBy) {
        patentMetadataRepository.findById(patentId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.PATENT_NOT_FOUND));
        if (content == null || content.length == 0) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "업로드할 PDF 파일이 비어 있습니다.");
        }
        if (content.length > MAX_PDF_BYTES) {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "PDF 파일은 50MB를 넘을 수 없습니다.");
        }
        if (content.length < 4 || content[0] != '%' || content[1] != 'P' || content[2] != 'D' || content[3] != 'F') {
            throw new PatentFlowException(ErrorCode.INVALID_REQUEST, "PDF 형식 파일만 업로드할 수 있습니다.");
        }
        String safeDocName = docName == null || docName.isBlank() ? patentId + ".pdf" : docName.trim();
        // I6: S3가 활성화돼 있으면 본문을 S3에 두고(DB 비대화 방지) presigned 링크 발급도 가능해진다.
        S3Client s3Client = properties.usable() ? s3ClientProvider.getIfAvailable() : null;
        PatentPdfDocumentEntity meta;
        if (s3Client != null) {
            String s3Key = keyPrefix() + "uploads/" + patentId + ".pdf";
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(s3Key)
                            .contentType("application/pdf")
                            .contentDisposition("attachment; filename=\"%s\"".formatted(safeDocName))
                            .build(),
                    RequestBody.fromBytes(content));
            meta = PatentPdfDocumentEntity.uploadedToS3(
                    patentId, s3Key, safeDocName, (long) content.length, uploadedBy, OffsetDateTime.now(KST));
            pdfContentRepository.deleteById(patentId);
        } else {
            meta = PatentPdfDocumentEntity.uploaded(
                    patentId, safeDocName, (long) content.length, uploadedBy, OffsetDateTime.now(KST));
            pdfContentRepository.save(new PatentPdfContentEntity(patentId, content));
        }
        pdfDocumentRepository.save(meta);
        return toMeta(meta);
    }

    /** MAIL-13: 업로드된 PDF 본문 — 업로드본이 있을 때만 내려준다(KIPRIS 캐시는 presigned로 제공). */
    @Transactional(readOnly = true)
    public PdfDownload downloadUploaded(String patentId) {
        PatentPdfDocumentEntity meta = pdfDocumentRepository.findById(patentId)
                .filter(PatentPdfDocumentEntity::isUploaded)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.PATENT_NOT_FOUND, "업로드된 특허 PDF가 없습니다."));
        // I6: S3 저장 업로드본은 S3에서, 그 외에는 DB 본문에서 내려준다.
        if (meta.getS3Key() != null) {
            S3Client s3Client = s3ClientProvider.getIfAvailable();
            if (s3Client == null) {
                throw new PatentFlowException(ErrorCode.INTERNAL_ERROR, "PDF 저장소(S3)에 접근할 수 없습니다.");
            }
            byte[] bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(meta.getS3Key())
                    .build()).asByteArray();
            return new PdfDownload(meta.getDocName(), bytes);
        }
        PatentPdfContentEntity content = pdfContentRepository.findById(patentId)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.PATENT_NOT_FOUND, "업로드된 특허 PDF가 없습니다."));
        return new PdfDownload(meta.getDocName(), content.getContent());
    }

    @Transactional(readOnly = true)
    public PatentPdfMetaResponse meta(String patentId) {
        return pdfDocumentRepository.findById(patentId)
                .map(this::toMeta)
                .orElse(PatentPdfMetaResponse.none(patentId));
    }

    /** MAIL-13: 업로드본 삭제(법무팀 교체·정리용). KIPRIS 캐시 행은 이 경로로 지우지 않는다. */
    @Transactional
    public void deleteUploaded(String patentId) {
        PatentPdfDocumentEntity meta = pdfDocumentRepository.findById(patentId)
                .filter(PatentPdfDocumentEntity::isUploaded)
                .orElseThrow(() -> new PatentFlowException(ErrorCode.PATENT_NOT_FOUND, "업로드된 특허 PDF가 없습니다."));
        if (meta.getS3Key() != null) {
            S3Client s3Client = s3ClientProvider.getIfAvailable();
            if (s3Client != null) {
                try {
                    s3Client.deleteObject(builder -> builder.bucket(properties.bucket()).key(meta.getS3Key()));
                } catch (Exception exception) {
                    log.warn("업로드 PDF S3 객체 삭제 실패(메타는 삭제 진행). patentId={}", patentId, exception);
                }
            }
        }
        pdfContentRepository.deleteById(patentId);
        pdfDocumentRepository.delete(meta);
    }

    private PatentPdfMetaResponse toMeta(PatentPdfDocumentEntity entity) {
        return new PatentPdfMetaResponse(
                entity.getPatentId(),
                true,
                entity.getStorageType(),
                entity.getDocName(),
                entity.getContentLength(),
                entity.getUploadedBy(),
                entity.getCreatedAt());
    }

    public record PdfDownload(String docName, byte[] content) {
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
