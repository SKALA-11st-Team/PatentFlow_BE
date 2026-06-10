package com.syuuk.patentflow.settings.repository;

import com.syuuk.patentflow.settings.domain.ValuationCriteriaConfigEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValuationCriteriaConfigRepository extends JpaRepository<ValuationCriteriaConfigEntity, String> {

    Optional<ValuationCriteriaConfigEntity> findTopByOrderByVersionDesc();

    List<ValuationCriteriaConfigEntity> findAllByOrderByVersionDesc();
}
