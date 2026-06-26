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
    /** Requested provider slug: {@code mock} or {@code mimo}. */
    private final String requestedProvider;
    /** Whether the requested provider was actually used (no fallback). */
    private final boolean providerHit;

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
        this.findings = findings;
        this.providerName = providerName;
        this.successful = successful;
        this.message = message;
        this.requestedProvider = requestedProvider;
        this.providerHit = providerHit;
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

    public String getProviderUsed() {
        if (providerName == null) {
            return null;
        }
        if (providerName.toLowerCase().contains("mimo")) {
            return "mimo";
        }
        return "mock";
    }
}
