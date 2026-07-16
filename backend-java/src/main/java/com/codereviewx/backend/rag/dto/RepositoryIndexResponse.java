package com.codereviewx.backend.rag.dto;

public record RepositoryIndexResponse(long jobId, String status, String repository, String requestedRef) {}
