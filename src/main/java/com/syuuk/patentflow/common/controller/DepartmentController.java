package com.syuuk.patentflow.common.controller;

import com.syuuk.patentflow.common.dto.DepartmentResponse;
import com.syuuk.patentflow.common.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private static final List<DepartmentResponse> DEPARTMENTS = List.of(
            new DepartmentResponse("DEPT-RND", "R&D본부"),
            new DepartmentResponse("DEPT-PLATFORM", "플랫폼사업부"),
            new DepartmentResponse("DEPT-ESG", "ESG사업부"),
            new DepartmentResponse("DEPT-ICT", "ICT사업부"),
            new DepartmentResponse("DEPT-MFG", "제조사업부"),
            new DepartmentResponse("DEPT-BIZ", "사업기획팀"));

    @GetMapping
    public ApiResponse<List<DepartmentResponse>> getDepartments() {
        return ApiResponse.ok(DEPARTMENTS);
    }
}
