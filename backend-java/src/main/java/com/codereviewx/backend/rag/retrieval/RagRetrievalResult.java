package com.codereviewx.backend.rag.retrieval;

import java.util.List;
import java.util.Objects;

public record RagRetrievalResult(Status status, Long snapshotId, int vectorCandidateCount,
                                 int lexicalCandidateCount,
                                 List<RagRetrievedChunk> matches,
                                 RagRetrievalHealth retrievalHealth) {

    public RagRetrievalResult {
        Objects.requireNonNull(status, "status");
        if (vectorCandidateCount < 0 || lexicalCandidateCount < 0) {
            throw new IllegalArgumentException("candidate counts must be non-negative");
        }
        matches = List.copyOf(Objects.requireNonNull(matches, "matches"));
        Objects.requireNonNull(retrievalHealth, "retrievalHealth");
    }

    public boolean legacyFallbackRequired() {
        return retrievalHealth.requiresLegacyFallback();
    }

    public enum Status {
        READY,
        INDEX_NOT_READY
    }
}
