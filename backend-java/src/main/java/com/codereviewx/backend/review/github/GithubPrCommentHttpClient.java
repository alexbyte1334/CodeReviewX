package com.codereviewx.backend.review.github;

public interface GithubPrCommentHttpClient {

    GithubPrCommentHttpResponse publishPullRequestComment(String apiBaseUrl,
                                                          GithubPrCommentPublishRequest publishRequest,
                                                          String token,
                                                          int timeoutSeconds);

    default GithubPrCommentHttpResponse upsertPullRequestComment(
            String apiBaseUrl, GithubPrCommentPublishRequest publishRequest,
            String token, int timeoutSeconds) {
        return publishPullRequestComment(apiBaseUrl, publishRequest, token, timeoutSeconds);
    }
}
