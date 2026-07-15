package com.codereviewx.backend.rag.retrieval;

import java.util.List;
import java.util.Objects;

public record RagEvidenceBundle(List<RagEvidence> evidence, String promptBlock, DegradedReason reason,
                                RagContextAssembler.RetrievalHealth retrievalHealth) {
    public RagEvidenceBundle {
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        Objects.requireNonNull(promptBlock, "promptBlock");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(retrievalHealth, "retrievalHealth");
    }

    public boolean degraded() {
        return reason != DegradedReason.NONE
                || retrievalHealth == RagContextAssembler.RetrievalHealth.SINGLE_ROUTE_FAILED;
    }

    public boolean legacyFallbackRequired() {
        return retrievalHealth == RagContextAssembler.RetrievalHealth.EMBEDDING_FAILED
                || retrievalHealth == RagContextAssembler.RetrievalHealth.BOTH_ROUTES_FAILED;
    }

    public enum DegradedReason {
        NONE,
        RERANK_UNAVAILABLE
    }
}
