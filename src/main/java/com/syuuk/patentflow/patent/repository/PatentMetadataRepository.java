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

    // CONTRACT-09/DASH-08: 필터 드롭다운 옵션용 distinct 값(공백 제외, 정렬). 전체 특허 기준이라 서버 필터로
    // 목록이 좁혀져도 옵션은 줄지 않는다.
    @Query("select distinct p.country from PatentMetadataEntity p "
            + "where p.country is not null and trim(p.country) <> '' order by p.country")
    List<String> findDistinctCountries();

    @Query("select distinct p.businessArea from PatentMetadataEntity p "
            + "where p.businessArea is not null and trim(p.businessArea) <> '' order by p.businessArea")
    List<String> findDistinctBusinessAreas();

    @Query("select distinct p.technologyArea from PatentMetadataEntity p "
            + "where p.technologyArea is not null and trim(p.technologyArea) <> '' order by p.technologyArea")
    List<String> findDistinctTechnologyAreas();

    @Query("select distinct p.productName from PatentMetadataEntity p "
            + "where p.productName is not null and trim(p.productName) <> '' order by p.productName")
    List<String> findDistinctProductNames();
}
