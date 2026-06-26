package com.codereviewx.backend.review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codereviewx.review")
public class ReviewProperties {

    /**
     * Active review provider: {@code mock} or {@code mimo}.
     * Defaults to mimo; falls back to mock when {@code MIMO_API_KEY} is missing.
     */
    private String provider = "mimo";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isMimoMode() {
        return "mimo".equalsIgnoreCase(normalize(provider));
    }

    public boolean isMockMode() {
        return !isMimoMode();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
