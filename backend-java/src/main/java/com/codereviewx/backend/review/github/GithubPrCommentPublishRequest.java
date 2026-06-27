package com.codereviewx.backend.review.github;

public record GithubPrCommentPublishRequest(
        String owner,
        String repo,
        Integer prNumber,
        String commitId,
        String path,
        Integer line,
        String side,
        String body
) {
}
