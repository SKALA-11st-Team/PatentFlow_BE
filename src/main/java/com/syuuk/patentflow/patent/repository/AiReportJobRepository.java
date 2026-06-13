package com.syuuk.patentflow.patent.repository;

import com.syuuk.patentflow.patent.domain.AiReportJobEntity;
import com.syuuk.patentflow.patent.dto.AiReportJobStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiReportJobRepository extends JpaRepository<AiReportJobEntity, String> {

    Optional<AiReportJobEntity> findFirstByPatentIdAndStatusInOrderByRequestedAtDesc(
            String patentId,
            Collection<AiReportJobStatus> statuses);

    List<AiReportJobEntity> findByStatusIn(Collection<AiReportJobStatus> statuses);

    Optional<AiReportJobEntity> findFirstByPatentIdOrderByRequestedAtDesc(String patentId);
}
