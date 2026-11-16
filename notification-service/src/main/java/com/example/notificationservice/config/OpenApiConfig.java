package com.example.notificationservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

// swagger ui at /swagger-ui/index.html. The server is "/" so "try it out" calls the same host the page came from,
// directly or through the gateway
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Notification Service API", version = "1.0", description = "Stores and streams user notifications, sends emails and keeps the audit log."),
        servers = @Server(url = "/"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
