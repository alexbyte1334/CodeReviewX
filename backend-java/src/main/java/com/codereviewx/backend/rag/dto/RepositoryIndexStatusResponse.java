package com.codereviewx.backend.rag.dto;

public record RepositoryIndexStatusResponse(String status, String commitSha, Integer indexedChunks,
                                            String errorCode, String errorMessage) {}
