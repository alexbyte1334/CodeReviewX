package com.codereviewx.backend.rag.service;

public record RagIndexResolution(long repositoryId, long jobId, String commitSha, Status status) {
    public enum Status {
        QUEUED, READY
    }
}
