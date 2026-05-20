package com.syuuk.patentflow.patent.repository;

import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PatentMetadataRepository extends JpaRepository<PatentMetadataEntity, String>, JpaSpecificationExecutor<PatentMetadataEntity> {
}
