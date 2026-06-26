package com.codereviewx.backend.review.github;

public record GithubPrMetadata(
        String owner,
        String repo,
        int prNumber,
        String title,
        String authorLogin,
        String baseRef,
        String headRef,
        String baseSha,
        String headSha,
        String state,
        String createdAt,
        String updatedAt,
        Integer changedFiles,
        Integer additions,
        Integer deletions
) {
}
