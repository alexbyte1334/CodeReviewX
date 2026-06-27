package com.codereviewx.backend.review.github;

public record GithubPrDiffFile(
        String filename,
        String status,
        Integer additions,
        Integer deletions,
        Integer changes,
        Integer patchBytes,
        Boolean patchTruncated
) {
}
