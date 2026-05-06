package com.syuuk.patentflow.patent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "patentflow.lookup")
public record PatentLookupProperties(
        Kipris kipris,
        GooglePatents googlePatents
) {

    public record Kipris(
            boolean enabled,
            String baseUrl,
            String serviceKey
    ) {
    }

    public record GooglePatents(
            boolean enabled,
            String baseUrl
    ) {
    }
}
