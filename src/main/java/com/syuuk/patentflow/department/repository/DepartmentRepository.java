package com.syuuk.patentflow.department.repository;

import com.syuuk.patentflow.department.domain.DepartmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @description 사업부 마스터 데이터를 조회·저장하는 JPA repository.
 */
public interface DepartmentRepository extends JpaRepository<DepartmentEntity, String> {
}
