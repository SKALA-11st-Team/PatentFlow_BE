package com.syuuk.patentflow.patent.domain;

import com.syuuk.patentflow.common.domain.BaseEntity;
import com.syuuk.patentflow.patent.dto.PatentLifecycleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "patents")
public class PatentMetadataEntity extends BaseEntity {

    @Id
    @Column(name = "patent_id", length = 32)
    private String patentId;

    @Column(name = "management_number", nullable = false, unique = true, length = 64)
    private String managementNumber;

    @Column(name = "draft_title", length = 1000)
    private String draftTitle;

    @Column(name = "title", length = 1000)
    private String title;

    @Column(name = "business_area", length = 128)
    private String businessArea;

    @Column(name = "technology_area", length = 256)
    private String technologyArea;

    @Column(name = "product_name", length = 512)
    private String productName;

    @Column(name = "country", length = 16)
    private String country;

    @Column(name = "joint_application", length = 16)
    private String jointApplication;

    @Column(name = "co_applicant_name", length = 512)
    private String coApplicantName;

    @Enumerated(EnumType.STRING)
    @Column(name = "patent_status", length = 64)
    private PatentLifecycleStatus patentStatus;

    @Column(name = "application_date")
    private LocalDate applicationDate;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

    @Column(name = "application_number", length = 64)
    private String applicationNumber;

    @Column(name = "registration_number", length = 64)
    private String registrationNumber;

    @Column(name = "expected_expiration_date")
    private LocalDate expectedExpirationDate;

    @Column(name = "fee_due_date")
    private LocalDate feeDueDate;

    protected PatentMetadataEntity() {
    }

    public PatentMetadataEntity(
            String patentId,
            String managementNumber,
            String draftTitle,
            String title,
            String businessArea,
            String technologyArea,
            String productName,
            String country,
            String jointApplication,
            String coApplicantName,
            PatentLifecycleStatus patentStatus,
            LocalDate applicationDate,
            LocalDate registrationDate,
            String applicationNumber,
            String registrationNumber,
            LocalDate expectedExpirationDate,
            LocalDate feeDueDate
    ) {
        this.patentId = patentId;
        this.managementNumber = managementNumber;
        this.draftTitle = draftTitle;
        this.title = title;
        this.businessArea = businessArea;
        this.technologyArea = technologyArea;
        this.productName = productName;
        this.country = country;
        this.jointApplication = jointApplication;
        this.coApplicantName = coApplicantName;
        this.patentStatus = patentStatus;
        this.applicationDate = applicationDate;
        this.registrationDate = registrationDate;
        this.applicationNumber = applicationNumber;
        this.registrationNumber = registrationNumber;
        this.expectedExpirationDate = expectedExpirationDate;
        this.feeDueDate = feeDueDate;
    }

    public String getPatentId() {
        return patentId;
    }

    public String getManagementNumber() {
        return managementNumber;
    }

    public String getDraftTitle() {
        return draftTitle;
    }

    public String getTitle() {
        return title;
    }

    public String getBusinessArea() {
        return businessArea;
    }

    public String getTechnologyArea() {
        return technologyArea;
    }

    public String getProductName() {
        return productName;
    }

    public String getCountry() {
        return country;
    }

    public String getJointApplication() {
        return jointApplication;
    }

    public String getCoApplicantName() {
        return coApplicantName;
    }

    public PatentLifecycleStatus getPatentStatus() {
        return patentStatus;
    }

    public LocalDate getApplicationDate() {
        return applicationDate;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public LocalDate getExpectedExpirationDate() {
        return expectedExpirationDate;
    }

    public LocalDate getFeeDueDate() {
        return feeDueDate;
    }

    public void setFeeDueDate(LocalDate feeDueDate) {
        this.feeDueDate = feeDueDate;
    }

    public void setPatentStatus(PatentLifecycleStatus patentStatus) {
        this.patentStatus = patentStatus;
    }

}
