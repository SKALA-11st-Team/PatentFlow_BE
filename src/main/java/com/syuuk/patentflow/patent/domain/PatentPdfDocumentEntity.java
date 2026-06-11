package com.syuuk.patentflow.patent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * @relatedFR FR-LEGAL-13
 * MAIL-12: 특허 공개전문 PDF의 S3 캐시 메타데이터. 같은 특허의 PDF를 KIPRIS에서
 * 반복 다운로드하지 않기 위한 1특허-1행 캐시다(presigned URL은 호출 시마다 생성).
 */
@Entity
@Table(name = "patent_pdf_documents")
public class PatentPdfDocumentEntity {

    @Id
    @Column(name = "patent_id", length = 64)
    private String patentId;

    @Column(name = "s3_key", nullable = false, length = 512)
    private String s3Key;

    @Column(name = "doc_name", length = 255)
    private String docName;

    @Column(name = "source_path", columnDefinition = "TEXT")
    private String sourcePath;

    @Column(name = "content_length")
    private Long contentLength;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PatentPdfDocumentEntity() {
    }

    public PatentPdfDocumentEntity(
            String patentId, String s3Key, String docName, String sourcePath,
            Long contentLength, OffsetDateTime createdAt
    ) {
        this.patentId = patentId;
        this.s3Key = s3Key;
        this.docName = docName;
        this.sourcePath = sourcePath;
        this.contentLength = contentLength;
        this.createdAt = createdAt;
    }

    public String getPatentId() {
        return patentId;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
