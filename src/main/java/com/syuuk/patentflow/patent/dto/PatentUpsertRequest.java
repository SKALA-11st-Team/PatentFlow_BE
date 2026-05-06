package com.syuuk.patentflow.patent.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record PatentUpsertRequest(
        @NotBlank String managementNumber,
        @NotBlank String title,
        LocalDate applicationDate,
        String coApplicants,
        String country,
        LocalDate registrationDate,
        String applicationNumber,
        String registrationNumber,
        LocalDate expectedExpirationDate,
        String source,
        String businessArea,
        String technologyArea,
        String productName
) {
}
