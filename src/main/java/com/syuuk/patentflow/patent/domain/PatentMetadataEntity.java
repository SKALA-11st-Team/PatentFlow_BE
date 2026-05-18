package com.syuuk.patentflow.patent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "patents")
public class PatentMetadataEntity {

    @Id
    @Column(length = 32)
    private String patentId;

    @Column(nullable = false, unique = true, length = 64)
    private String managementNumber;

    @Column(length = 1000)
    private String draftTitle;

    @Column(length = 1000)
    private String title;

    @Column(length = 128)
    private String businessArea;

    @Column(length = 256)
    private String technologyArea;

    @Column(length = 512)
    private String productName;

    @Column(length = 16)
    private String country;

    @Column(length = 16)
    private String jointApplication;

    @Column(length = 512)
    private String coApplicantName;

    @Column(length = 64)
    private String patentStatus;

    private LocalDate applicationDate;

    private LocalDate registrationDate;

    @Column(length = 64)
    private String applicationNumber;

    @Column(length = 64)
    private String registrationNumber;

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
            String patentStatus,
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

    public String getPatentStatus() {
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

    public void setPatentStatus(String patentStatus) {
        this.patentStatus = patentStatus;
    }

}
