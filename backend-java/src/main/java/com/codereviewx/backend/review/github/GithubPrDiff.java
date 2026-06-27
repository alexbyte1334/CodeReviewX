package com.codereviewx.backend.review.github;

import java.util.List;

public record GithubPrDiff(
        String diffText,
        Integer fileCount,
        Integer diffBytes,
        Boolean diffTruncated,
        List<GithubPrDiffFile> files
) {
}
