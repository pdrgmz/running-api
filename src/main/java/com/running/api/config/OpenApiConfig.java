package com.running.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    /** Orden de presentación de las etiquetas en Swagger UI. */
    private static final List<String> TAG_ORDER = List.of(
            "Autenticación",
            "Actividades",
            "Archivos",
            "Estadísticas y Récords",
            "Analítica Deportiva",
            "Exportación Geoespacial",
            "MCP (Model Context Protocol)");

    @Value("${app.openapi.server-url:https://running-api.pdrgmz.uk}")
    private String serverUrl;

    @Bean
    public OpenAPI runningApiOpenAPI() {
        Server relativeServer = new Server().url("/").description("Servidor actual (relativo)");
        Server configuredServer = new Server().url(serverUrl).description("Servidor de producción");

        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Token JWT obtenido en POST /api/v1/auth/login o /api/v1/auth/register. "
                        + "Introdúcelo como 'Bearer <token>'.");

        return new OpenAPI()
                .servers(List.of(relativeServer, configuredServer))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .info(new Info()
                        .title("Running Tracker & Analytics API")
                        .version("1.3.0")
                        .description("""
                                API REST para la ingesta de actividades de carrera (TCX, GPX y FIT), analítica
                                fisiológica avanzada, cálculo de récords personales (PB), exportación
                                geoespacial (GeoJSON/GPX) y respaldo de archivos.

                                **Autenticación:** la mayoría de los endpoints requieren un JWT. Obtén un token
                                con `/api/v1/auth/register` o `/api/v1/auth/login` y pulsa **Authorize** para
                                introducirlo. Cada usuario solo puede acceder a sus propios datos.""")
                        .contact(new Contact().name("Running API Support").email("support@running.local"))
                        .license(new License().name("Uso personal")));
    }

    /**
     * Deduplica y ordena las etiquetas (evita duplicados cuando coinciden los
     * {@code @Tag} de los controladores) y fija el orden de presentación.
     */
    @Bean
    public OpenApiCustomizer tagOrderCustomizer() {
        return openApi -> {
            if (openApi.getTags() == null || openApi.getTags().isEmpty()) {
                return;
            }
            Map<String, Tag> byName = new LinkedHashMap<>();
            for (Tag tag : openApi.getTags()) {
                byName.putIfAbsent(tag.getName(), tag);
            }
            List<Tag> ordered = new ArrayList<>();
            for (String name : TAG_ORDER) {
                Tag tag = byName.remove(name);
                if (tag != null) {
                    ordered.add(tag);
                }
            }
            ordered.addAll(byName.values());
            openApi.setTags(ordered);
        };
    }
}
