package com.syuuk.patentflow.business.repository;

import com.syuuk.patentflow.business.domain.BusinessSubmissionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessSubmissionRepository extends JpaRepository<BusinessSubmissionEntity, String> {

    long countByPatentId(String patentId);

    List<BusinessSubmissionEntity> findByPatentIdOrderByVersionAsc(String patentId);
}
