package com.supportai.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** How the API is grouped in the Swagger UI. */
@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi authenticationApi() {
        return GroupedOpenApi.builder()
                .group("1-authentication")
                .pathsToMatch("/api/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi incidentApi() {
        return GroupedOpenApi.builder()
                .group("2-incidents")
                .pathsToMatch("/api/incidents/**")
                .build();
    }

    @Bean
    public GroupedOpenApi knowledgeApi() {
        return GroupedOpenApi.builder()
                .group("3-knowledge")
                .pathsToMatch("/api/knowledge/**")
                .build();
    }

    @Bean
    public GroupedOpenApi assistantApi() {
        return GroupedOpenApi.builder()
                .group("4-assistant")
                .pathsToMatch("/api/assistant/**")
                .build();
    }

    @Bean
    public GroupedOpenApi operationsApi() {
        return GroupedOpenApi.builder()
                .group("5-operations")
                .pathsToMatch("/api/dashboard/**", "/api/audit/**")
                .build();
    }
}
