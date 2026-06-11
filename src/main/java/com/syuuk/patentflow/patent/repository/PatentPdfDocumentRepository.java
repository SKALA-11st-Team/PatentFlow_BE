package com.syuuk.patentflow.patent.repository;

import com.syuuk.patentflow.patent.domain.PatentPdfDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatentPdfDocumentRepository extends JpaRepository<PatentPdfDocumentEntity, String> {
}
