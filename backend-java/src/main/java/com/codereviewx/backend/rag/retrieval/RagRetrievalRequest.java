package com.codereviewx.backend.rag.retrieval;

import java.util.Objects;

public record RagRetrievalRequest(long repositoryId, String commitSha,
                                  RagRetrievalQuery query) {

    public RagRetrievalRequest {
        if (repositoryId <= 0) {
            throw new IllegalArgumentException("repositoryId must be positive");
        }
        commitSha = Objects.requireNonNull(commitSha, "commitSha");
        if (commitSha.isBlank()) {
            throw new IllegalArgumentException("commitSha must not be blank");
        }
        Objects.requireNonNull(query, "query");
    }
}
