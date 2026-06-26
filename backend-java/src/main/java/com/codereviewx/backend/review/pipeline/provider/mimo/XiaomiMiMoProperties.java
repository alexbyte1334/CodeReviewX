package com.codereviewx.backend.review.pipeline.provider.mimo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codereviewx.ai.mimo")
public class XiaomiMiMoProperties {

    private String baseUrl = "https://api.xiaomimimo.com/v1";
    private String model = "mimo-v2.5-pro";
    /** Legacy single-key compatibility only; new dual-agent flow requires role keys. */
    private String apiKey = "";
    private String plannerApiKey = "";
    private String executorApiKey = "";
    /** Connect and read timeout for MiMo HTTP calls (seconds). */
    private int timeoutSeconds = 60;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getPlannerApiKey() {
        return plannerApiKey;
    }

    public void setPlannerApiKey(String plannerApiKey) {
        this.plannerApiKey = plannerApiKey;
    }

    public String getExecutorApiKey() {
        return executorApiKey;
    }

    public void setExecutorApiKey(String executorApiKey) {
        this.executorApiKey = executorApiKey;
    }

    public boolean hasPlannerApiKey() {
        return plannerApiKey != null && !plannerApiKey.isBlank();
    }

    public boolean hasExecutorApiKey() {
        return executorApiKey != null && !executorApiKey.isBlank();
    }

    public boolean hasRoleApiKeys() {
        return hasPlannerApiKey() && hasExecutorApiKey();
    }

    @Override
    public String toString() {
        return "XiaomiMiMoProperties{"
                + "baseUrl='" + baseUrl + '\''
                + ", model='" + model + '\''
                + ", apiKey='***'"
                + ", plannerApiKey='***'"
                + ", executorApiKey='***'"
                + '}';
    }
}
