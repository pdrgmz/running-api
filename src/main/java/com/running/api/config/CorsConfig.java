package com.running.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,https://running-api.pdrgmz.uk,http://running-api.pdrgmz.uk}")
    private String[] allowedOrigins;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**") // Aplica a todas las rutas de la API
                        .allowedOriginPatterns(allowedOrigins) // Orígenes permitidos (soporta patrones y credenciales)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS") // Métodos HTTP
                        .allowedHeaders("*") // Permite todos los encabezados
                        .exposedHeaders("Authorization", "Content-Disposition") // Encabezados visibles al frontend
                        .allowCredentials(true) // Permite cookies / autenticación
                        .maxAge(3600); // Tiempo de cache del Preflight (en segundos)
            }
        };
    }
}