package com.ibizabroker.bibliotheque.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI (springdoc-openapi) : http://localhost:8080/swagger-ui.html
 *
 * Le bouton "Authorize" permet de coller un token JWT (obtenu via
 * POST /authenticate) pour tester les endpoints protégés directement
 * depuis Swagger.
 */
@Configuration
public class SwaggerConfiguration {

    private static final String SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI bibliothequeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Bibliothèque")
                        .description("API de gestion de bibliothèque — module Réservation (Séance 2/4)")
                        .version("1.0"))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SCHEME_NAME,
                        new SecurityScheme()
                                .name(SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Collez ici le jwtToken renvoyé par POST /authenticate")));
    }
}
