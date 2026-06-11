package com.syuuk.patentflow.patent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.syuuk.patentflow.mailing.dto.PatentPdfLinkResponse;
import com.syuuk.patentflow.patent.client.KiprisPdfPathClient;
import com.syuuk.patentflow.patent.config.PatentPdfStorageProperties;
import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import com.syuuk.patentflow.patent.domain.PatentPdfDocumentEntity;
import com.syuuk.patentflow.patent.repository.PatentMetadataRepository;
import com.syuuk.patentflow.patent.repository.PatentPdfDocumentRepository;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

class PatentPdfServiceTest {

    private static final PatentPdfStorageProperties ENABLED_PROPS =
            new PatentPdfStorageProperties(true, "test-bucket", "ap-northeast-2", "patent-pdfs/", 7);
    private static final PatentPdfStorageProperties DISABLED_PROPS =
            new PatentPdfStorageProperties(false, "", "ap-northeast-2", "patent-pdfs/", 7);

    private final KiprisPdfPathClient kiprisPdfPathClient = mock(KiprisPdfPathClient.class);
    private final PatentPdfDocumentRepository pdfDocumentRepository = mock(PatentPdfDocumentRepository.class);
    private final PatentMetadataRepository patentMetadataRepository = mock(PatentMetadataRepository.class);
    private final S3Client s3Client = mock(S3Client.class);
    private final S3Presigner s3Presigner = mock(S3Presigner.class);

    @SuppressWarnings("unchecked")
    private PatentPdfService service(PatentPdfStorageProperties props) {
        ObjectProvider<S3Client> s3Provider = mock(ObjectProvider.class);
        when(s3Provider.getIfAvailable()).thenReturn(s3Client);
        ObjectProvider<S3Presigner> presignerProvider = mock(ObjectProvider.class);
        when(presignerProvider.getIfAvailable()).thenReturn(s3Presigner);
        return new PatentPdfService(
                props, kiprisPdfPathClient, pdfDocumentRepository, patentMetadataRepository,
                s3Provider, presignerProvider);
    }

    private PatentMetadataEntity patent(String patentId, String country) {
        return new PatentMetadataEntity(
                patentId, "M-" + patentId, "", "테스트 특허", "Data", "AI", "제품", country,
                "N", null, null, null, null,
                "10-2024-0115774", "10-2932891", null, null);
    }

    private void stubPresign(String url) throws MalformedURLException {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create(url).toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
    }

    // MAIL-12: 비KR 특허는 KIPRIS PDF 대상이 아니므로 원문 URL 폴백.
    @Test
    void nonKoreanPatentFallsBackToOriginalUrl() {
        when(patentMetadataRepository.findById("PAT-US")).thenReturn(Optional.of(patent("PAT-US", "US")));

        List<PatentPdfLinkResponse> links = service(ENABLED_PROPS).resolvePdfLinks(List.of("PAT-US"));

        assertThat(links).hasSize(1);
        assertThat(links.get(0).source()).isEqualTo(PatentPdfLinkResponse.SOURCE_ORIGINAL_URL);
        assertThat(links.get(0).pdfUrl()).contains("patents.google.com/patent/US");
    }

    // MAIL-12: 저장소 비활성(enabled=false)이면 KR 특허도 원문 URL 폴백 — 무해 배포 보장.
    @Test
    void disabledStorageFallsBackToOriginalUrl() {
        when(patentMetadataRepository.findById("PAT-KR")).thenReturn(Optional.of(patent("PAT-KR", "KR")));

        List<PatentPdfLinkResponse> links = service(DISABLED_PROPS).resolvePdfLinks(List.of("PAT-KR"));

        assertThat(links.get(0).source()).isEqualTo(PatentPdfLinkResponse.SOURCE_ORIGINAL_URL);
        verify(kiprisPdfPathClient, never()).findPdfPath(anyString());
    }

    // MAIL-12: 캐시 적중 시 KIPRIS 재호출·재업로드 없이 presigned 링크만 새로 만든다.
    @Test
    void cacheHitPresignsWithoutRedownload() throws MalformedURLException {
        when(patentMetadataRepository.findById("PAT-KR")).thenReturn(Optional.of(patent("PAT-KR", "KR")));
        when(pdfDocumentRepository.findById("PAT-KR")).thenReturn(Optional.of(new PatentPdfDocumentEntity(
                "PAT-KR", "patent-pdfs/PAT-KR.pdf", "1020240115774.pdf", "http://kipris/path", 1024L,
                OffsetDateTime.now())));
        stubPresign("https://test-bucket.s3.ap-northeast-2.amazonaws.com/patent-pdfs/PAT-KR.pdf?X-Amz-Signature=x");

        List<PatentPdfLinkResponse> links = service(ENABLED_PROPS).resolvePdfLinks(List.of("PAT-KR"));

        assertThat(links.get(0).source()).isEqualTo(PatentPdfLinkResponse.SOURCE_KIPRIS_S3);
        assertThat(links.get(0).pdfUrl()).contains("X-Amz-Signature");
        assertThat(links.get(0).expiresAt()).isNotNull();
        verify(kiprisPdfPathClient, never()).findPdfPath(anyString());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    // MAIL-12: 신규 PDF — KIPRIS 경로 조회 → 다운로드 → S3 업로드 → 캐시 저장 → presign.
    @Test
    void freshPdfDownloadsUploadsAndCaches() throws MalformedURLException {
        when(patentMetadataRepository.findById("PAT-KR")).thenReturn(Optional.of(patent("PAT-KR", "KR")));
        when(pdfDocumentRepository.findById("PAT-KR")).thenReturn(Optional.empty());
        when(kiprisPdfPathClient.findPdfPath("10-2024-0115774")).thenReturn(Optional.of(
                new KiprisPdfPathClient.KiprisPdfPath("http://plus.kipris.or.kr/file.pdf", "1020240115774.pdf")));
        stubPresign("https://test-bucket.s3.ap-northeast-2.amazonaws.com/patent-pdfs/PAT-KR.pdf?X-Amz-Signature=y");

        PatentPdfService spied = spy(service(ENABLED_PROPS));
        doReturn("%PDF-1.7 test".getBytes()).when(spied).download("http://plus.kipris.or.kr/file.pdf");

        List<PatentPdfLinkResponse> links = spied.resolvePdfLinks(List.of("PAT-KR"));

        assertThat(links.get(0).source()).isEqualTo(PatentPdfLinkResponse.SOURCE_KIPRIS_S3);
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(pdfDocumentRepository).save(any(PatentPdfDocumentEntity.class));
    }

    // MAIL-12: KIPRIS 공개전문 부재(미공개 특허 등)면 원문 URL 폴백 — 하드 실패 금지.
    @Test
    void missingKiprisPdfFallsBackToOriginalUrl() {
        when(patentMetadataRepository.findById("PAT-KR")).thenReturn(Optional.of(patent("PAT-KR", "KR")));
        when(pdfDocumentRepository.findById("PAT-KR")).thenReturn(Optional.empty());
        when(kiprisPdfPathClient.findPdfPath(anyString())).thenReturn(Optional.empty());

        List<PatentPdfLinkResponse> links = service(ENABLED_PROPS).resolvePdfLinks(List.of("PAT-KR"));

        assertThat(links.get(0).source()).isEqualTo(PatentPdfLinkResponse.SOURCE_ORIGINAL_URL);
        assertThat(links.get(0).pdfUrl()).contains("patents.google.com/patent/KR");
    }

    // MAIL-12: 존재하지 않는 특허는 url 없이 원문 폴백 표시(메일 흐름 차단 금지).
    @Test
    void unknownPatentReturnsFallbackWithoutUrl() {
        when(patentMetadataRepository.findById("PAT-NONE")).thenReturn(Optional.empty());

        List<PatentPdfLinkResponse> links = service(ENABLED_PROPS).resolvePdfLinks(List.of("PAT-NONE"));

        assertThat(links.get(0).source()).isEqualTo(PatentPdfLinkResponse.SOURCE_ORIGINAL_URL);
        assertThat(links.get(0).pdfUrl()).isNull();
    }
}
