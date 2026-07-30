package com.codereviewx.backend.config;

import com.codereviewx.backend.demo.DemoProperties;
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
    private final DemoProperties demoProperties;

    public WebConfig(DemoProperties demoProperties) {
        this.demoProperties = demoProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        demoProperties.getAllowedOrigins().toArray(String[]::new)
                )
                .allowedMethods("GET", "POST", "PATCH", "OPTIONS")
                .allowedHeaders("Content-Type", "Idempotency-Key", "Last-Event-ID", "Authorization")
                .exposedHeaders("Location")
                .maxAge(3600);
    }
}
