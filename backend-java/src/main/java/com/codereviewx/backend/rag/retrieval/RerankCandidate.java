package com.codereviewx.backend.rag.retrieval;

import java.util.Objects;

public record RerankCandidate(String chunkId, String text) {

    public RerankCandidate {
        Objects.requireNonNull(chunkId, "chunkId");
        Objects.requireNonNull(text, "text");
        if (chunkId.isBlank()) {
            throw new IllegalArgumentException("Rerank candidate chunk id must not be blank");
        }
    }

    @Override
    public String toString() {
        return "RerankCandidate{chunkId='" + chunkId + "'}";
    }
}
