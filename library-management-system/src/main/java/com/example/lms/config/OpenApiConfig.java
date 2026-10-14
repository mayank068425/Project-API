package com.example.lms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI document: title, version and the security scheme.
 *
 * <p>Split from {@link SwaggerConfig}, which configures the UI. They are listed
 * separately in the project layout and genuinely do different things — this one
 * describes the API, that one describes how it is displayed — so neither is a
 * duplicate of the other.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI libraryOpenApi() {
        final String scheme = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Library Management System API")
                        .version("1.0.0")
                        .description(
                                "Catalogue, membership, circulation, fines and reporting. "
                                + "Two behaviours worth knowing before calling anything. "
                                + "Fines are computed in the library's own timezone, so a "
                                + "return at 23:30 local on the due date is on time. And a "
                                + "returned copy is held for the first member in the "
                                + "reservation queue rather than shelved, so a successful "
                                + "return may not make the book available.")
                        .contact(new Contact().name("Library Management System"))
                        .license(new License().name("MIT")))
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme()
                                .name(scheme)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the accessToken from /api/auth/login")));
    }
}
