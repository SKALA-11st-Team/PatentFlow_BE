package com.syuuk.patentflow.department.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * @description 사업부 계정, 메일 수신자 매핑, 특허 담당 부서 표시에서 공통으로 사용하는 사업부 마스터 엔티티.
 */
@Entity
@Table(name = "departments")
public class DepartmentEntity {

    @Id
    @Column(length = 64)
    private String departmentId;

    @Column(length = 128)
    private String departmentName;

    private LocalDate updatedAt;

    protected DepartmentEntity() {
    }

    public DepartmentEntity(String departmentId, String departmentName, LocalDate updatedAt) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.updatedAt = updatedAt;
    }

    public String getDepartmentId() { return departmentId; }
    public String getDepartmentName() { return departmentName; }
    public LocalDate getUpdatedAt() { return updatedAt; }

    public void rename(String departmentName, LocalDate updatedAt) {
        this.departmentName = departmentName;
        this.updatedAt = updatedAt;
    }
}
