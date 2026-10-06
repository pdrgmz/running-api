package com.running.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Value("${app.openapi.server-url:https://running-api.pdrgmz.uk}")
    private String serverUrl;

    @Bean
    public OpenAPI runningApiOpenAPI() {
        return new OpenAPI()
                .servers(List.of(new Server().url(serverUrl).description("Servidor de Producción")))
                .info(new Info()
                        .title("Running Tracker & Analytics API")
                        .description("API REST de alto rendimiento para ingesta TCX, analítica fisiológica, cálculo de récords personales (PB), exportación GeoJSON/GPX y respaldo.")
                        .version("1.1.0")
                        .contact(new Contact().name("Running API Support")));
    }
}
