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

    public ReviewProviderResult(List<ReviewFinding> findings,
                                String providerName,
                                boolean successful,
                                String message) {
        this.findings = findings;
        this.providerName = providerName;
        this.successful = successful;
        this.message = message;
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
}
