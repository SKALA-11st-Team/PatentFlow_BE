package com.syuuk.patentflow.patent.dto;

import java.time.LocalDate;

public record PatentBibliographicInfoResponse(
        String managementNumber,
        String title,
        LocalDate applicationDate,
        String coApplicants,
        String country,
        LocalDate registrationDate,
        String applicationNumber,
        String registrationNumber,
        LocalDate expectedExpirationDate,
        String source
) {
}
