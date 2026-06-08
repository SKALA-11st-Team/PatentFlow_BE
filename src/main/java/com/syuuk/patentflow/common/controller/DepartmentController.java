package com.syuuk.patentflow.common.controller;

import com.syuuk.patentflow.common.dto.DepartmentResponse;
import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.mailing.repository.DepartmentRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    public DepartmentController(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @GetMapping
    public ApiResponse<List<DepartmentResponse>> getDepartments() {
        return ApiResponse.ok(departmentRepository.findAll().stream()
                .map(department -> new DepartmentResponse(department.getDepartmentId(), department.getDepartmentName()))
                .toList());
    }
}
