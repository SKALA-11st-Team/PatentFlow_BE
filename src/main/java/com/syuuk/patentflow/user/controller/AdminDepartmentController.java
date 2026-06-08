package com.syuuk.patentflow.user.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.mailing.dto.DepartmentRecipientMappingResponse;
import com.syuuk.patentflow.user.dto.CreateDepartmentRequest;
import com.syuuk.patentflow.user.dto.PageResponse;
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

@RestController
@RequestMapping("/api/v1/admin/departments")
public class AdminDepartmentController {

    private final AdminDepartmentService adminDepartmentService;

    public AdminDepartmentController(AdminDepartmentService adminDepartmentService) {
        this.adminDepartmentService = adminDepartmentService;
    }

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

    @PostMapping
    public ApiResponse<DepartmentRecipientMappingResponse> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request) {
        return ApiResponse.ok(adminDepartmentService.createDepartment(request));
    }

    @PutMapping("/{departmentId}")
    public ApiResponse<DepartmentRecipientMappingResponse> updateDepartment(
            @PathVariable String departmentId,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        return ApiResponse.ok(adminDepartmentService.updateDepartment(departmentId, request));
    }

    @DeleteMapping("/{departmentId}")
    public ApiResponse<Void> deleteDepartment(@PathVariable String departmentId) {
        adminDepartmentService.deleteDepartment(departmentId);
        return ApiResponse.ok(null);
    }
}
