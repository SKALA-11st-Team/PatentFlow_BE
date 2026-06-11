package com.syuuk.patentflow.patent.repository;

import com.syuuk.patentflow.patent.domain.PatentPdfContentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatentPdfContentRepository extends JpaRepository<PatentPdfContentEntity, String> {
}
