package com.codereviewx.backend.review.dto;

public class TraceSummaryResponse {

    private int toolCount;
    private int failedToolCount;
    private boolean providerFallback;

    public TraceSummaryResponse() {
    }

    public TraceSummaryResponse(int toolCount, int failedToolCount, boolean providerFallback) {
        this.toolCount = toolCount;
        this.failedToolCount = failedToolCount;
        this.providerFallback = providerFallback;
    }

    public int getToolCount() {
        return toolCount;
    }

    public void setToolCount(int toolCount) {
        this.toolCount = toolCount;
    }

    public int getFailedToolCount() {
        return failedToolCount;
    }

    public void setFailedToolCount(int failedToolCount) {
        this.failedToolCount = failedToolCount;
    }

    public boolean isProviderFallback() {
        return providerFallback;
    }

    public void setProviderFallback(boolean providerFallback) {
        this.providerFallback = providerFallback;
    }
}
