package com.syuuk.patentflow.patent.client;

public record PatentLookupQuery(
        String keyword,
        String applicationNumber,
        String registrationNumber,
        String country
) {
}
