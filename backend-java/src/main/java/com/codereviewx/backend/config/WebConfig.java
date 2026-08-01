package com.codereviewx.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Minimal CORS configuration for local frontend development.
 * Allows requests from the Vite dev server (localhost:5173 / 127.0.0.1:5173).
 * No Spring Security is introduced.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    public WebConfig(@Value("${codereviewx.cors.allowed-origins:http://localhost:5173}") String origins) {
        this.allowedOrigins = java.util.Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        allowedOrigins
                )
                .allowedMethods("GET", "POST", "PATCH", "OPTIONS")
                .allowedHeaders("Content-Type", "Idempotency-Key", "Last-Event-ID", "Authorization")
                .exposedHeaders("Location")
                .maxAge(3600);
    }
}
