package com.banking.config;

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
 * The OpenAPI document: title, version and the bearer scheme.
 *
 * <p>Separate from {@link SwaggerConfig}, which configures the UI. The two are
 * listed separately in the project layout and genuinely do different things —
 * this describes the API, that describes how it is displayed.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankingOpenApi() {
        final String scheme = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Banking Account Management API")
                        .version("1.0.0")
                        .description(
                                "Customers, accounts, transactions and beneficiaries.\n\n"
                                + "Two behaviours worth knowing before calling anything. "
                                + "A transfer writes two ledger rows sharing one reference, "
                                + "so any account's balance is the signed sum of its own "
                                + "rows. And a newly verified beneficiary is capped for a "
                                + "cooling-off window, so a large transfer to a payee added "
                                + "minutes ago is refused by design.")
                        .contact(new Contact().name("Banking API"))
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
