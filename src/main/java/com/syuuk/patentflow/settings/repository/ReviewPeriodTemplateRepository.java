package com.syuuk.patentflow.settings.repository;

import com.syuuk.patentflow.settings.domain.ReviewPeriodTemplateEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewPeriodTemplateRepository extends JpaRepository<ReviewPeriodTemplateEntity, Integer> {

    List<ReviewPeriodTemplateEntity> findAllByOrderByPeriodNumber();
}
