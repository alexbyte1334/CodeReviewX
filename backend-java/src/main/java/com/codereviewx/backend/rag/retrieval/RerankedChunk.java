package com.codereviewx.backend.rag.retrieval;

import java.util.Objects;

public record RerankedChunk(RerankCandidate candidate, double score) {

    public RerankedChunk {
        Objects.requireNonNull(candidate, "candidate");
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("Rerank score must be finite");
        }
    }
}
