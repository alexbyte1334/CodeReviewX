package com.codereviewx.backend.rag.dto;

public record RepositoryIndexStatusResponse(String status, String commitSha, Integer indexedChunks,
                                            String errorCode, String errorMessage,
                                            String phase, Integer processedFiles, Integer totalFiles,
                                            java.time.LocalDateTime lastProgressAt,
                                            java.time.LocalDateTime deadlineAt) {
    public RepositoryIndexStatusResponse(String status, String commitSha, Integer indexedChunks,
                                         String errorCode, String errorMessage) {
        this(status, commitSha, indexedChunks, errorCode, errorMessage, null, null, null, null, null);
    }
}
