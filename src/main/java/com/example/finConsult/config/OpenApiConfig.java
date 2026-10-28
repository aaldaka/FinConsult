package com.example.finConsult.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the Swagger/OpenAPI document metadata and the bearer-token security
 * scheme used by every protected endpoint.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "FinConsult API",
                version = "1.0",
                description = "Financial consultation booking platform: advisors, availability slots, appointments, KYC, advisor applications and real-time notifications. "
                        + "Operations that two users can trigger at the same time return 409 Conflict when they lose the race. "
                        + "Uploaded documents and certificates are checked in the background."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Paste the token returned by POST /auth/login (without the word Bearer)")
public class OpenApiConfig {
}