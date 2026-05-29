package com.syuuk.patentflow.patent.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "patentflow.lookup")
public record PatentLookupProperties(
        Kipris kipris,
        GooglePatents googlePatents
) {

    public record Kipris(
            String baseUrl,
            String serviceKey,
            List<String> serviceKeys
    ) {
    }

    public record GooglePatents(
            boolean enabled,
            String baseUrl
    ) {
    }
}
