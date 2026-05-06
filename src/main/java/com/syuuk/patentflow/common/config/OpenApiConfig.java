package com.syuuk.patentflow.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI patentFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PatentFlow BE API")
                        .version("v1")
                        .description("PatentFlow FE 우선 연동 API 문서"));
    }
}
