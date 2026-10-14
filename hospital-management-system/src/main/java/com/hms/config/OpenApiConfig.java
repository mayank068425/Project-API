package com.hms.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hospitalOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Hospital Management System API")
                .version("1.0.0")
                .description("Patients, doctors and departments. "
                        + "Note that patient age is derived from date of birth and is "
                        + "never stored, and that deleting a department or doctor with "
                        + "dependants is refused rather than cascaded.")
                .contact(new Contact().name("HMS")));
    }
}
