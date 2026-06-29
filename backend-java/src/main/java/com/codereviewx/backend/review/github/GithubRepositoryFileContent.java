package com.codereviewx.backend.review.github;

public record GithubRepositoryFileContent(
        String path,
        String content,
        Integer sizeBytes,
        Boolean truncated
) {
}
