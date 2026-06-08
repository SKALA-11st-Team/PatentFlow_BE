package com.syuuk.patentflow.patent.repository;

import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import com.syuuk.patentflow.patent.dto.PatentLifecycleStatus;
import java.time.LocalDate;
import java.util.List;
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

    List<PatentMetadataEntity> findAllByOrderByFeeDueDateAscManagementNumberAsc();

    List<PatentMetadataEntity> findByCountryIgnoreCaseOrderByFeeDueDateAscManagementNumberAsc(String country);

    /**
     * 연차료 납부 기준일이 지난 보유 중(ACTIVE) 특허를 조회한다.
     * 목록 조회 시 과기 특허를 포기 상태로 일괄 보정하기 위한 용도.
     */
    List<PatentMetadataEntity> findByPatentStatusAndFeeDueDateBefore(PatentLifecycleStatus status, LocalDate date);
}
