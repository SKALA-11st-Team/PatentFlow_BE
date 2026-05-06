package com.syuuk.patentflow.patent.dto;

public record PatentContextSuggestionRequest(
        String managementNumber,
        String title,
        String applicationDate,
        String coApplicants,
        String country,
        String registrationDate,
        String applicationNumber,
        String registrationNumber,
        String expectedExpirationDate,
        String source,
        String businessArea,
        String technologyArea,
        String productName
) {
}
