/**
 * @author 유건욱
 * @date 2026-05-19
 */
package com.syuuk.patentflow.common.controller;

import com.syuuk.patentflow.common.dto.DepartmentResponse;
import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.mailing.repository.DepartmentRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @relatedFR FR-LEGAL-12
 * @relatedUI UI-LEGAL-07, UI-LEGAL-08
 * @description 공통 부서 조회 API. 부서 선택 드롭다운 등에서 쓰는 부서 ID·이름 목록을 제공한다.
 */
@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    public DepartmentController(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    /**
     * @relatedFR FR-LEGAL-12
     * @relatedUI UI-LEGAL-07, UI-LEGAL-08
     * @description 전체 부서의 ID·이름 목록을 조회한다.
     */
    @GetMapping
    public ApiResponse<List<DepartmentResponse>> getDepartments() {
        return ApiResponse.ok(departmentRepository.findAll().stream()
                .map(department -> new DepartmentResponse(department.getDepartmentId(), department.getDepartmentName()))
                .toList());
    }
}
