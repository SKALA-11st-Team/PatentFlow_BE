package com.syuuk.patentflow.patent.repository;

import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface PatentMetadataRepository extends JpaRepository<PatentMetadataEntity, String>, JpaSpecificationExecutor<PatentMetadataEntity> {

    @Query("""
            select coalesce(max(cast(substring(p.patentId, 10) as integer)), 0)
            from PatentMetadataEntity p
            where p.patentId like 'PAT-2026-%'
            """)
    int findMaxPatentSequence();
}
