package com.syuuk.patentflow.patent.repository;

import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatentMetadataRepository extends JpaRepository<PatentMetadataEntity, String> {
}
