package com.codereviewx.backend.review.github;

public interface GithubPrMetadataHttpClient {

    GithubPrMetadataHttpResponse fetchPullRequest(
            String apiBaseUrl,
            GithubRepositoryRef repository,
            int prNumber,
            String token,
            int timeoutSeconds
    );
}
