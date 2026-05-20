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

    @Column(length = 256)
    private String managerEmail;

    @Column(length = 128)
    private String managerName;

    @Column(columnDefinition = "TEXT")
    private String ccEmailsJson;

    private LocalDate updatedAt;

    protected DepartmentEntity() {
    }

    public DepartmentEntity(String departmentId, String departmentName, LocalDate updatedAt) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.updatedAt = updatedAt;
    }

    public void updateRecipientMapping(
            String departmentName,
            String managerEmail,
            String managerName,
            String ccEmailsJson,
            LocalDate updatedAt
    ) {
        this.departmentName = departmentName;
        this.managerEmail = managerEmail;
        this.managerName = managerName;
        this.ccEmailsJson = ccEmailsJson;
        this.updatedAt = updatedAt;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getManagerEmail() {
        return managerEmail;
    }

    public String getManagerName() {
        return managerName;
    }

    public String getCcEmailsJson() {
        return ccEmailsJson;
    }

    public LocalDate getUpdatedAt() {
        return updatedAt;
    }
}
