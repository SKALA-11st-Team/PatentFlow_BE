/**
 * @author 유건욱
 * @date 2026-06-11
 */
package com.syuuk.patentflow.patent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * @relatedFR FR-LEGAL-13
 * MAIL-12: 특허 PDF 문서 메타데이터(1특허-1행).
 * storage_type=KIPRIS_S3 — KIPRIS 공개전문을 S3에 캐시한 경우(s3_key 사용, presigned URL 발급).
 * storage_type=UPLOADED — 법무팀이 직접 업로드한 경우(MAIL-13, TW·UAE 등 KIPRIS 미지원 국가).
 *   본문 바이트는 patent_pdf_contents에 분리 저장해 목록/메일 해석 경로가 대용량을 로드하지 않게 한다.
 */
@Entity
@Table(name = "patent_pdf_documents")
public class PatentPdfDocumentEntity {

    public static final String STORAGE_KIPRIS_S3 = "KIPRIS_S3";
    public static final String STORAGE_UPLOADED = "UPLOADED";

    @Id
    @Column(name = "patent_id", length = 64)
    private String patentId;

    @Column(name = "storage_type", nullable = false, length = 32)
    private String storageType;

    @Column(name = "s3_key", length = 512)
    private String s3Key;

    @Column(name = "doc_name", length = 255)
    private String docName;

    @Column(name = "source_path", columnDefinition = "TEXT")
    private String sourcePath;

    @Column(name = "content_length")
    private Long contentLength;

    @Column(name = "uploaded_by", length = 255)
    private String uploadedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PatentPdfDocumentEntity() {
    }

    private PatentPdfDocumentEntity(
            String patentId, String storageType, String s3Key, String docName, String sourcePath,
            Long contentLength, String uploadedBy, OffsetDateTime createdAt
    ) {
        this.patentId = patentId;
        this.storageType = storageType;
        this.s3Key = s3Key;
        this.docName = docName;
        this.sourcePath = sourcePath;
        this.contentLength = contentLength;
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
    }

    public static PatentPdfDocumentEntity kiprisS3Cache(
            String patentId, String s3Key, String docName, String sourcePath,
            Long contentLength, OffsetDateTime createdAt
    ) {
        return new PatentPdfDocumentEntity(
                patentId, STORAGE_KIPRIS_S3, s3Key, docName, sourcePath, contentLength, null, createdAt);
    }

    public static PatentPdfDocumentEntity uploaded(
            String patentId, String docName, Long contentLength, String uploadedBy, OffsetDateTime createdAt
    ) {
        return new PatentPdfDocumentEntity(
                patentId, STORAGE_UPLOADED, null, docName, null, contentLength, uploadedBy, createdAt);
    }

    /** I6: S3 활성화 시 업로드본 — 본문은 S3(s3Key)에 두고 DB에는 메타만 남긴다. */
    public static PatentPdfDocumentEntity uploadedToS3(
            String patentId, String s3Key, String docName, Long contentLength, String uploadedBy, OffsetDateTime createdAt
    ) {
        return new PatentPdfDocumentEntity(
                patentId, STORAGE_UPLOADED, s3Key, docName, null, contentLength, uploadedBy, createdAt);
    }

    public boolean isUploaded() {
        return STORAGE_UPLOADED.equals(storageType);
    }

    public String getPatentId() {
        return patentId;
    }

    public String getStorageType() {
        return storageType;
    }

    public String getS3Key() {
        return s3Key;
    }

    public String getDocName() {
        return docName;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public Long getContentLength() {
        return contentLength;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
