package com.syuuk.patentflow.mailing.repository;

import com.syuuk.patentflow.mailing.domain.MailingHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailingHistoryRepository extends JpaRepository<MailingHistoryEntity, String> {

    boolean existsByDepartmentId(String departmentId);
}
