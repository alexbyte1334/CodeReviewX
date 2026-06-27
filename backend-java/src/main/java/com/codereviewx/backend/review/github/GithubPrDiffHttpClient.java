package com.codereviewx.backend.review.github;

public interface GithubPrDiffHttpClient {

    GithubPrDiffHttpResponse fetchPullRequestFiles(String apiBaseUrl,
                                                   GithubRepositoryRef repository,
                                                   int prNumber,
                                                   String token,
                                                   int timeoutSeconds,
                                                   int maxFilesToFetch);
}
