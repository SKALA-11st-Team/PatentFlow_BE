package com.syuuk.patentflow;

import com.syuuk.patentflow.patent.config.PatentLookupProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PatentLookupProperties.class)
public class PatentFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatentFlowApplication.class, args);
    }
}
