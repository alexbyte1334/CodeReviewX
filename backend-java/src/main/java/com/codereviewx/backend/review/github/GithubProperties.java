package com.codereviewx.backend.review.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codereviewx.github")
public class GithubProperties {

    private String apiBaseUrl = "https://api.github.com";
    private String token = "";
    private int timeoutSeconds = 20;
    private int maxChangedFiles = 50;
    private int maxDiffBytes = 512000;
    private int perFilePatchMaxBytes = 20000;

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

    public int getMaxChangedFiles() {
        return maxChangedFiles;
    }

    public void setMaxChangedFiles(int maxChangedFiles) {
        this.maxChangedFiles = maxChangedFiles;
    }

    public int getMaxDiffBytes() {
        return maxDiffBytes;
    }

    public void setMaxDiffBytes(int maxDiffBytes) {
        this.maxDiffBytes = maxDiffBytes;
    }

    public int getPerFilePatchMaxBytes() {
        return perFilePatchMaxBytes;
    }

    public void setPerFilePatchMaxBytes(int perFilePatchMaxBytes) {
        this.perFilePatchMaxBytes = perFilePatchMaxBytes;
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
                + ", maxChangedFiles=" + maxChangedFiles
                + ", maxDiffBytes=" + maxDiffBytes
                + ", perFilePatchMaxBytes=" + perFilePatchMaxBytes
                + '}';
    }
}
