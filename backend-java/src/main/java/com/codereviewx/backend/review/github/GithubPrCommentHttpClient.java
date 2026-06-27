package com.codereviewx.backend.review.github;

public interface GithubPrCommentHttpClient {

    GithubPrCommentHttpResponse publishPullRequestComment(String apiBaseUrl,
                                                          GithubPrCommentPublishRequest publishRequest,
                                                          String token,
                                                          int timeoutSeconds);
}
