/**
 * @author 유건욱
 * @date 2026-06-11
 */
package com.syuuk.patentflow.patent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * @relatedFR FR-LEGAL-13
 * MAIL-13: 법무팀이 직접 업로드한 특허 PDF의 본문 바이트.
 * 메타(patent_pdf_documents)와 분리해 다운로드 시에만 로드한다 — 메일 링크 해석·목록 경로가
 * 수십 MB 바이트를 끌어오지 않게 하기 위한 의도적 1:1 분리다.
 */
@Entity
@Table(name = "patent_pdf_contents")
public class PatentPdfContentEntity {

    @Id
    @Column(name = "patent_id", length = 64)
    private String patentId;

    @Column(name = "content", nullable = false)
    private byte[] content;

    protected PatentPdfContentEntity() {
    }

    public PatentPdfContentEntity(String patentId, byte[] content) {
        this.patentId = patentId;
        this.content = content;
    }

    public String getPatentId() {
        return patentId;
    }

    public byte[] getContent() {
        return content;
    }
}
