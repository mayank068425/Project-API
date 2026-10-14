package com.example.lms.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * How the API is grouped in the Swagger UI.
 *
 * <p>Distinct from {@link OpenApiConfig}, which defines the document itself.
 * This one splits the endpoints into groups so the UI is navigable: a single
 * flat list of every endpoint in a system this size is a page nobody reads to
 * the bottom of.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi catalogueApi() {
        return GroupedOpenApi.builder()
                .group("1-catalogue")
                .pathsToMatch("/api/books/**", "/api/authors/**", "/api/categories/**")
                .build();
    }

    @Bean
    public GroupedOpenApi circulationApi() {
        return GroupedOpenApi.builder()
                .group("2-circulation")
                .pathsToMatch("/api/issues/**", "/api/returns/**", "/api/reservations/**",
                        "/api/fines/**")
                .build();
    }

    @Bean
    public GroupedOpenApi membershipApi() {
        return GroupedOpenApi.builder()
                .group("3-membership")
                .pathsToMatch("/api/members/**", "/api/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi reportingApi() {
        return GroupedOpenApi.builder()
                .group("4-reporting")
                .pathsToMatch("/api/reports/**", "/api/dashboard/**")
                .build();
    }
}
