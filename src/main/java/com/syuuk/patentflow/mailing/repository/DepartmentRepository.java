package com.syuuk.patentflow.mailing.repository;

import com.syuuk.patentflow.mailing.domain.DepartmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<DepartmentEntity, String> {

    Page<DepartmentEntity> findByDepartmentIdContainingIgnoreCaseOrDepartmentNameContainingIgnoreCase(
            String departmentId,
            String departmentName,
            Pageable pageable);
}
