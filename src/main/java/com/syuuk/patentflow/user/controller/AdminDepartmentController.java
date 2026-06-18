/**
 * @author 유건욱
 * @date 2026-05-19
 */
package com.syuuk.patentflow.user.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.mailing.dto.DepartmentRecipientMappingResponse;
import com.syuuk.patentflow.user.dto.CreateDepartmentRequest;
import com.syuuk.patentflow.common.response.PageResponse;
import com.syuuk.patentflow.user.dto.UpdateDepartmentRequest;
import com.syuuk.patentflow.user.service.AdminDepartmentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @relatedFR FR-LEGAL-12
 * @relatedUI UI-LEGAL-07, UI-LEGAL-08
 * @description 관리자 부서(수신자 매핑) 관리 API. 부서/수신자 매핑의 조회·등록·수정·삭제를 제공한다.
 */
@RestController
@RequestMapping("/api/v1/admin/departments")
public class AdminDepartmentController {

    private final AdminDepartmentService adminDepartmentService;

    public AdminDepartmentController(AdminDepartmentService adminDepartmentService) {
        this.adminDepartmentService = adminDepartmentService;
    }

    /**
     * @relatedFR FR-LEGAL-12
     * @relatedUI UI-LEGAL-07, UI-LEGAL-08
     * @description 부서 목록을 조회한다. page/size/search가 모두 없으면 전체 목록, 있으면 검색·페이징 결과를 반환한다.
     */
    @GetMapping
    public ApiResponse<?> getDepartments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search) {
        if (page == null && size == null && (search == null || search.isBlank())) {
            return ApiResponse.ok(adminDepartmentService.getDepartments());
        }
        PageResponse<DepartmentRecipientMappingResponse> departments = adminDepartmentService.getDepartments(
                page != null ? page : 0,
                size != null ? size : 20,
                search);
        return ApiResponse.ok(departments);
    }

    /**
     * @relatedFR FR-LEGAL-12
     * @relatedUI UI-LEGAL-07, UI-LEGAL-08
     * @description 부서(수신자 매핑)를 신규 등록한다.
     */
    @PostMapping
    public ApiResponse<DepartmentRecipientMappingResponse> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request) {
        return ApiResponse.ok(adminDepartmentService.createDepartment(request));
    }

    /**
     * @relatedFR FR-LEGAL-12
     * @relatedUI UI-LEGAL-07, UI-LEGAL-08
     * @description 부서명을 수정한다.
     */
    @PutMapping("/{departmentId}")
    public ApiResponse<DepartmentRecipientMappingResponse> updateDepartment(
            @PathVariable String departmentId,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        return ApiResponse.ok(adminDepartmentService.updateDepartment(departmentId, request));
    }

    /**
     * @relatedFR FR-LEGAL-12
     * @relatedUI UI-LEGAL-07, UI-LEGAL-08
     * @description 부서를 삭제한다(소속 계정·검토 이력·메일 발송 이력이 있으면 삭제 불가).
     */
    @DeleteMapping("/{departmentId}")
    public ApiResponse<Void> deleteDepartment(@PathVariable String departmentId) {
        adminDepartmentService.deleteDepartment(departmentId);
        return ApiResponse.ok(null);
    }
}
