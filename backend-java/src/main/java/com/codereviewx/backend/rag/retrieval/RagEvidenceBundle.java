package com.codereviewx.backend.rag.retrieval;

import java.util.List;
import java.util.Objects;

public record RagEvidenceBundle(List<RagEvidence> evidence, String promptBlock, boolean degraded,
                                DegradedReason reason, boolean legacyFallbackRequired) {
    public RagEvidenceBundle {
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        Objects.requireNonNull(promptBlock, "promptBlock");
        if (!degraded && reason != DegradedReason.NONE) {
            throw new IllegalArgumentException("Non-degraded evidence cannot have a degraded reason");
        }
        if (degraded && reason == DegradedReason.NONE) {
            throw new IllegalArgumentException("Degraded evidence requires a reason");
        }
    }

    public enum DegradedReason {
        NONE,
        RERANK_UNAVAILABLE
    }
}
