package com.codereviewx.backend.review.dto;

public class ProviderSummaryResponse {

    private String requestedProvider;
    private String providerUsed;
    private Boolean providerHit;
    private String modelName;
    private String outputSummary;
    private Integer findingCount;
    private String fallbackReason;

    public String getRequestedProvider() {
        return requestedProvider;
    }

    public void setRequestedProvider(String requestedProvider) {
        this.requestedProvider = requestedProvider;
    }

    public String getProviderUsed() {
        return providerUsed;
    }

    public void setProviderUsed(String providerUsed) {
        this.providerUsed = providerUsed;
    }

    public Boolean getProviderHit() {
        return providerHit;
    }

    public void setProviderHit(Boolean providerHit) {
        this.providerHit = providerHit;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public void setOutputSummary(String outputSummary) {
        this.outputSummary = outputSummary;
    }

    public Integer getFindingCount() {
        return findingCount;
    }

    public void setFindingCount(Integer findingCount) {
        this.findingCount = findingCount;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }
}
