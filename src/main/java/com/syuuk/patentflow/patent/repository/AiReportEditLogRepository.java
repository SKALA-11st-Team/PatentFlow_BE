package com.syuuk.patentflow.patent.repository;

import com.syuuk.patentflow.patent.domain.AiReportEditLogEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiReportEditLogRepository extends JpaRepository<AiReportEditLogEntity, String> {

    List<AiReportEditLogEntity> findByPatentIdOrderByEditedAtDesc(String patentId);
}
