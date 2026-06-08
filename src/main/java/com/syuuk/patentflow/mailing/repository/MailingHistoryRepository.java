package com.syuuk.patentflow.mailing.repository;

import com.syuuk.patentflow.mailing.domain.MailingHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailingHistoryRepository extends JpaRepository<MailingHistoryEntity, String> {

    boolean existsByDepartmentId(String departmentId);

    Page<MailingHistoryEntity> findByRecipientEmailIgnoreCase(String recipientEmail, Pageable pageable);

    Page<MailingHistoryEntity> findByPatentsJsonContaining(String patentId, Pageable pageable);

    Page<MailingHistoryEntity> findByRecipientEmailIgnoreCaseAndPatentsJsonContaining(
            String recipientEmail,
            String patentId,
            Pageable pageable);
}
