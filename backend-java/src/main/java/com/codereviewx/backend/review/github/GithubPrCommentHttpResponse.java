package com.codereviewx.backend.review.github;

public record GithubPrCommentHttpResponse(
        int statusCode,
        boolean rateLimited,
        Long commentId
) {
}
