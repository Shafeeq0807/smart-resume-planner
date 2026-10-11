package com.smartresume.planner.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI: adds the "Authorize" button for the JWT access token. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI smartResumePlannerOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Smart Resume Planner API").version("0.1.0")
                        .description("ATS scoring, job matching, AI resume optimizer, application tracker, "
                                + "feedback chatbot, GitHub analyzer and PDF export."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
