package com.syuuk.patentflow.patent.repository;

import com.syuuk.patentflow.patent.domain.PatentMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PatentMetadataRepository extends JpaRepository<PatentMetadataEntity, String>, JpaSpecificationExecutor<PatentMetadataEntity> {
    
    // [최적화] 전체 데이터를 메모리로 로드하여 max()를 구하는 O(N) 연산을 방지하기 위한 DB 위임 집계 쿼리
    @Query("SELECT MAX(CAST(SUBSTRING(p.patentId, 10) AS int)) FROM PatentMetadataEntity p WHERE p.patentId LIKE 'PAT-2026-%'")
    Integer findMaxPatentSequence();

    // [최적화] Java Stream 필터링 대신 DB 단에서 국가 코드 필터링 및 정렬을 수행하여 메모리 낭비 방지
    @Query("SELECT p FROM PatentMetadataEntity p WHERE :country IS NULL OR p.country = :country ORDER BY p.feeDueDate ASC, p.managementNumber ASC")
    List<PatentMetadataEntity> findByCountryOrderByFeeDueDateAndManagementNumber(@Param("country") String country);
}
