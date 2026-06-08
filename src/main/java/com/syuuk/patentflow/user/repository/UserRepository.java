package com.syuuk.patentflow.user.repository;

import com.syuuk.patentflow.user.domain.UserEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findFirstByRoleOrderByCreatedAtAsc(String role);

    List<UserEntity> findByRoleOrderByCreatedAtAsc(String role);

    List<UserEntity> findByRoleAndIdNot(String role, String id);

    boolean existsByEmail(String email);

    boolean existsByDepartmentId(String departmentId);

    boolean existsByRole(String role);

    boolean existsByRoleAndIdNot(String role, String id);

    long countByRole(String role);

    Page<UserEntity> findByEmailContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrDepartmentIdContainingIgnoreCase(
            String email,
            String username,
            String departmentId,
            Pageable pageable);
}
