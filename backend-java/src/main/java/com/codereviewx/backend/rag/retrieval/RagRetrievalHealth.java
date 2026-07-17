package com.codereviewx.backend.rag.retrieval;

public enum RagRetrievalHealth {
    HEALTHY,
    SINGLE_ROUTE_FAILED,
    BOTH_ROUTES_FAILED,
    EMBEDDING_FAILED;

    public boolean requiresLegacyFallback() {
        return this == EMBEDDING_FAILED || this == BOTH_ROUTES_FAILED;
    }
}
