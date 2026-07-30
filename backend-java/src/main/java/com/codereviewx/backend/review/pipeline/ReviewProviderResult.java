package com.codereviewx.backend.review.pipeline;

import java.util.List;

/**
 * Minimal provider execution result wrapper.
 * Internal only; not persisted or exposed through the public API.
 */
public class ReviewProviderResult {

    private final List<ReviewFinding> findings;
    private final String providerName;
    private final boolean successful;
    private final String message;
    /** Requested provider slug. New review tasks currently use {@code mimo}. */
    private final String requestedProvider;
    /** Whether the requested provider was actually used (no fallback). */
    private final boolean providerHit;
    private final Integer promptTokens;
    private final Integer completionTokens;
    private final Integer totalTokens;

    public ReviewProviderResult(List<ReviewFinding> findings,
                                String providerName,
                                boolean successful,
                                String message) {
        this(findings, providerName, successful, message, null, false);
    }

    public ReviewProviderResult(List<ReviewFinding> findings,
                                String providerName,
                                boolean successful,
                                String message,
                                String requestedProvider,
                                boolean providerHit) {
        this(findings, providerName, successful, message, requestedProvider,
                providerHit, null, null, null);
    }

    public ReviewProviderResult(List<ReviewFinding> findings,
                                String providerName,
                                boolean successful,
                                String message,
                                String requestedProvider,
                                boolean providerHit,
                                Integer promptTokens,
                                Integer completionTokens,
                                Integer totalTokens) {
        this.findings = findings;
        this.providerName = providerName;
        this.successful = successful;
        this.message = message;
        this.requestedProvider = requestedProvider;
        this.providerHit = providerHit;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    public List<ReviewFinding> getFindings() {
        return findings;
    }

    public String getProviderName() {
        return providerName;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getMessage() {
        return message;
    }

    public String getRequestedProvider() {
        return requestedProvider;
    }

    public boolean isProviderHit() {
        return providerHit;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public String getProviderUsed() {
        if (providerName == null) {
            return null;
        }
        String normalizedProviderName = providerName.toLowerCase();
        if (normalizedProviderName.contains("mimo")) {
            return "mimo";
        }
        if (normalizedProviderName.contains("mock")) {
            return "mock";
        }
        return null;
    }
}
