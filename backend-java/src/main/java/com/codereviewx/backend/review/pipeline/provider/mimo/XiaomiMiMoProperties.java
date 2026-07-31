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
    private int timeoutSeconds = 45;
    /** Hard output budget shared by the bounded planner, executor and gatekeeper calls. */
    private int maxCompletionTokens = 1024;
    // Reasoning tokens are included in MiMo's completion budget. The real PR
    // planner needs the full bounded budget to reach its final JSON response.
    private int plannerMaxCompletionTokens = 1024;
    private int executorMaxCompletionTokens = 1024;
    private int gatekeeperMaxCompletionTokens = 384;

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

    public int getMaxCompletionTokens() {
        return maxCompletionTokens;
    }

    public void setMaxCompletionTokens(int maxCompletionTokens) {
        this.maxCompletionTokens = Math.max(64, maxCompletionTokens);
    }

    public int getPlannerMaxCompletionTokens() {
        return plannerMaxCompletionTokens;
    }

    public void setPlannerMaxCompletionTokens(int plannerMaxCompletionTokens) {
        this.plannerMaxCompletionTokens = Math.max(64, plannerMaxCompletionTokens);
    }

    public int getExecutorMaxCompletionTokens() {
        return executorMaxCompletionTokens;
    }

    public void setExecutorMaxCompletionTokens(int executorMaxCompletionTokens) {
        this.executorMaxCompletionTokens = Math.max(64, executorMaxCompletionTokens);
    }

    public int getGatekeeperMaxCompletionTokens() {
        return gatekeeperMaxCompletionTokens;
    }

    public void setGatekeeperMaxCompletionTokens(int gatekeeperMaxCompletionTokens) {
        this.gatekeeperMaxCompletionTokens = Math.max(64, gatekeeperMaxCompletionTokens);
    }

    int maxCompletionTokensFor(String systemPrompt) {
        if (ReviewPromptBuilder.PLANNER_SYSTEM_PROMPT.equals(systemPrompt)) {
            return Math.min(maxCompletionTokens, plannerMaxCompletionTokens);
        }
        if (ReviewPromptBuilder.EXECUTOR_SYSTEM_PROMPT.equals(systemPrompt)) {
            return Math.min(maxCompletionTokens, executorMaxCompletionTokens);
        }
        if (ReviewPromptBuilder.GATEKEEPER_SYSTEM_PROMPT.equals(systemPrompt)) {
            return Math.min(maxCompletionTokens, gatekeeperMaxCompletionTokens);
        }
        return maxCompletionTokens;
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
                + ", maxCompletionTokens=" + maxCompletionTokens
                + ", plannerMaxCompletionTokens=" + plannerMaxCompletionTokens
                + ", executorMaxCompletionTokens=" + executorMaxCompletionTokens
                + ", gatekeeperMaxCompletionTokens=" + gatekeeperMaxCompletionTokens
                + '}';
    }
}
