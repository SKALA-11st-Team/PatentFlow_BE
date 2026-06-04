package com.syuuk.patentflow.mailing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

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
