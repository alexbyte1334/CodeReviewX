package com.codereviewx.backend.review.service;

public record RepositoryContextFile(
        String path,
        String language,
        Integer sizeBytes,
        Boolean truncated,
        String content
) {
}
