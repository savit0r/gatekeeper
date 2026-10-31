package com.gatekeeper.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gatekeeperOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Gatekeeper API Key & Rate-Limiting Engine")
                        .description("Portfolio-grade API key lifecycle management and atomic Redis rate-limiting platform.")
                        .version("1.0.0")
                        .contact(new Contact().name("Gatekeeper Project").url("https://github.com/savit0r/gatekeeper")))
                .components(new Components()
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                                .name("X-API-Key")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Consumer API Key for accessing protected API endpoints"))
                        .addSecuritySchemes("DeveloperAuth", new SecurityScheme()
                                .name("X-Developer-Id")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Developer ID for managing API keys and viewing analytics")));
    }
}
