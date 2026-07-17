package com.codereviewx.backend.rag.retrieval;

import java.util.List;
import java.util.Objects;

public record RagEvidenceBundle(List<RagEvidence> evidence, String promptBlock, DegradedReason reason,
                                RagRetrievalHealth retrievalHealth) {
    public RagEvidenceBundle(List<RagEvidence> evidence, String promptBlock, boolean degraded,
                             DegradedReason reason, boolean legacyFallbackRequired) {
        this(evidence, promptBlock, reason,
                legacyFallbackRequired ? RagRetrievalHealth.EMBEDDING_FAILED
                        : degraded && reason == DegradedReason.NONE
                        ? RagRetrievalHealth.SINGLE_ROUTE_FAILED
                        : RagRetrievalHealth.HEALTHY);
    }

    public RagEvidenceBundle {
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        long uniqueLabels = evidence.stream().map(RagEvidence::label).distinct().count();
        if (evidence.stream().anyMatch(item -> item.label() == null || item.label().isBlank())
                || uniqueLabels != evidence.size()) {
            throw new IllegalArgumentException("Evidence labels must be unique and non-blank");
        }
        Objects.requireNonNull(promptBlock, "promptBlock");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(retrievalHealth, "retrievalHealth");
    }

    public boolean degraded() {
        return reason != DegradedReason.NONE
                || retrievalHealth == RagRetrievalHealth.SINGLE_ROUTE_FAILED;
    }

    public boolean legacyFallbackRequired() {
        return retrievalHealth == RagRetrievalHealth.EMBEDDING_FAILED
                || retrievalHealth == RagRetrievalHealth.BOTH_ROUTES_FAILED;
    }

    public enum DegradedReason {
        NONE,
        RERANK_UNAVAILABLE
    }
}
