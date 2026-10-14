package com.supportai.config;

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
 * The OpenAPI document. Separate from {@link SwaggerConfig}, which groups the UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI supportOpenApi() {
        final String scheme = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Production Support Assistant API")
                        .version("1.0.0")
                        .description(
                                "Incident management with a retrieval-augmented assistant "
                                + "over your runbooks.\n\n"
                                + "Two behaviours worth knowing before calling anything. "
                                + "Every assistant output is verified against what the model "
                                + "was actually shown, and anything unverifiable is WITHHELD "
                                + "rather than returned with a caveat — so a 422 from "
                                + "/api/assistant is often the system working correctly. And "
                                + "the assistant is off by default: incidents, search, the "
                                + "dashboard and audit all work without it.")
                        .contact(new Contact().name("Production Support Assistant"))
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
