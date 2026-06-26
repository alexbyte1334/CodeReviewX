package com.codereviewx.backend.review.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codereviewx.github")
public class GithubProperties {

    private String apiBaseUrl = "https://api.github.com";
    private String token = "";
    private int timeoutSeconds = 20;

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    @Override
    public String toString() {
        return "GithubProperties{"
                + "apiBaseUrl='" + apiBaseUrl + '\''
                + ", token='***'"
                + ", timeoutSeconds=" + timeoutSeconds
                + '}';
    }
}
