package com.codereviewx.backend.review.github;

public record GithubRepositoryFileContentHttpResponse(
        int statusCode,
        boolean rateLimited,
        GithubRepositoryFileContent content
) {
}
