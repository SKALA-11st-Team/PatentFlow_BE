package com.syuuk.patentflow.user.repository;

import com.syuuk.patentflow.user.domain.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByDepartmentId(String departmentId);

    // [최적화] 전체 유저를 조회 후 권한을 찾는 O(N) 탐색 대신, DB 인덱스 기반 존재 여부만 반환
    boolean existsByRole(String role);
}
