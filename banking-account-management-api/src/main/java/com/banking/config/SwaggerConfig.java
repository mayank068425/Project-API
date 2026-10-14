package com.banking.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * How the API is grouped in the Swagger UI.
 *
 * <p>Distinct from {@link OpenApiConfig}, which defines the document itself. A
 * single flat list of every endpoint is a page nobody reads to the bottom of.
 */
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
    public GroupedOpenApi customerApi() {
        return GroupedOpenApi.builder()
                .group("2-customers")
                .pathsToMatch("/api/customers/**")
                .build();
    }

    @Bean
    public GroupedOpenApi accountApi() {
        return GroupedOpenApi.builder()
                .group("3-accounts")
                .pathsToMatch("/api/accounts/**")
                .build();
    }

    @Bean
    public GroupedOpenApi transactionApi() {
        return GroupedOpenApi.builder()
                .group("4-transactions")
                .pathsToMatch("/api/transactions/**")
                .build();
    }

    @Bean
    public GroupedOpenApi beneficiaryApi() {
        return GroupedOpenApi.builder()
                .group("5-beneficiaries")
                .pathsToMatch("/api/beneficiaries/**")
                .build();
    }
}
