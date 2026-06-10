package com.syuuk.patentflow.business.repository;

import com.syuuk.patentflow.business.domain.BusinessChecklistItemEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessChecklistItemRepository extends JpaRepository<BusinessChecklistItemEntity, String> {

    List<BusinessChecklistItemEntity> findAllByOrderBySortOrderAsc();
}
