package com.syuuk.patentflow.settings.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 현재 활성 가치평가 기준(계약 C3). config는 agent 계약(C1)의 valuationConfig 형태 그대로다.
 *
 * @param isDefault 한 번도 설정된 적이 없어 기본값이 적용 중인지 여부(version 0).
 */
public record ValuationCriteriaResponse(
        Map<String, Object> config,
        boolean isDefault,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
