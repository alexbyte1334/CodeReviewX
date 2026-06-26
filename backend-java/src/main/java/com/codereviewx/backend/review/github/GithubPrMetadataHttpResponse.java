package com.codereviewx.backend.review.github;

public record GithubPrMetadataHttpResponse(
        int statusCode,
        boolean rateLimited,
        GithubPullRequestResponse body
) {
}
