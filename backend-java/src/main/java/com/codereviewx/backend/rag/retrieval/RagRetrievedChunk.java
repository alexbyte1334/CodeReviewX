package com.codereviewx.backend.rag.retrieval;

public record RagRetrievedChunk(long chunkId, String path, String language, String symbolName,
                                int startLine, int endLine, String contentHash, String content,
                                double pathBoost, double fusedScore) {
}
