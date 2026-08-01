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
        LocalDateTime heartbeatAt,
        String errorCode,
        String errorMessage,
        String embeddingModel,
        int embeddingDimensions,
        int indexVersion,
        int indexedChunkCount,
        int indexedFileCount,
        String phase,
        int totalFileCount,
        LocalDateTime lastProgressAt,
        LocalDateTime deadlineAt
) {
    public RagIndexJob(long id, long repositoryId, String requestedRef, String resolvedCommitSha, Status status,
                       int attemptCount, LocalDateTime startedAt, LocalDateTime finishedAt,
                       LocalDateTime heartbeatAt, String errorCode, String errorMessage, String embeddingModel,
                       int embeddingDimensions, int indexVersion, int indexedChunkCount) {
        this(id, repositoryId, requestedRef, resolvedCommitSha, status, attemptCount, startedAt, finishedAt,
                heartbeatAt, errorCode, errorMessage, embeddingModel, embeddingDimensions, indexVersion,
                indexedChunkCount, 0, "QUEUED", 0, null, null);
    }
    public RagIndexJob(long id,long repositoryId,String requestedRef,String resolvedCommitSha,Status status,int attemptCount,LocalDateTime startedAt,LocalDateTime finishedAt,LocalDateTime heartbeatAt,String errorCode,String errorMessage,String embeddingModel,int embeddingDimensions,int indexVersion) {
        this(id,repositoryId,requestedRef,resolvedCommitSha,status,attemptCount,startedAt,finishedAt,heartbeatAt,errorCode,errorMessage,embeddingModel,embeddingDimensions,indexVersion,0,0,"QUEUED",0,null,null);
    }
    public enum Status {
        QUEUED, RUNNING, READY, FAILED
    }
}
