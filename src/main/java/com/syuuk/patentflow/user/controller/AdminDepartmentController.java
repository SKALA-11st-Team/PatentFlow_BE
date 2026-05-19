package com.syuuk.patentflow.user.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.mailing.dto.DepartmentRecipientMappingResponse;
import com.syuuk.patentflow.user.dto.CreateDepartmentRequest;
import com.syuuk.patentflow.user.service.AdminDepartmentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/departments")
public class AdminDepartmentController {

    private final AdminDepartmentService adminDepartmentService;

    public AdminDepartmentController(AdminDepartmentService adminDepartmentService) {
        this.adminDepartmentService = adminDepartmentService;
    }

    @GetMapping
    public ApiResponse<List<DepartmentRecipientMappingResponse>> getDepartments() {
        return ApiResponse.ok(adminDepartmentService.getDepartments());
    }

    @PostMapping
    public ApiResponse<DepartmentRecipientMappingResponse> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request) {
        return ApiResponse.ok(adminDepartmentService.createDepartment(request));
    }

    @DeleteMapping("/{departmentId}")
    public ApiResponse<Void> deleteDepartment(@PathVariable String departmentId) {
        adminDepartmentService.deleteDepartment(departmentId);
        return ApiResponse.ok(null);
    }
}
