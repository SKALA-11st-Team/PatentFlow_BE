/**
 * @author 유건욱
 * @date 2026-06-12
 */
package com.syuuk.patentflow.patent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @relatedFR FR-LEGAL-02
 * F5: 특허 다건 부서 일괄 배정 요청.
 */
public record BulkAssignDepartmentRequest(
        @NotEmpty @Size(max = 200) List<String> patentIds,
        @NotBlank String departmentId
) {
}
