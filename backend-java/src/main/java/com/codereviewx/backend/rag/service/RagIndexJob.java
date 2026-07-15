package com.codereviewx.backend.rag.service;

import java.time.LocalDateTime;

public record RagIndexJob(
        long id,
        long repositoryId,
        String requestedRef,
        String resolvedCommitSha,
        Status status,
        int attemptCount,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String errorCode,
        String errorMessage,
        String embeddingModel,
        int embeddingDimensions,
        int indexVersion
) {
    public enum Status {
        QUEUED, RUNNING, READY, FAILED
    }
}
