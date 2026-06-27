package com.codereviewx.backend.review.github;

import java.util.List;

public record GithubPrDiffHttpResponse(
        int statusCode,
        boolean rateLimited,
        List<GithubPrDiffFileResponse> files
) {
}
