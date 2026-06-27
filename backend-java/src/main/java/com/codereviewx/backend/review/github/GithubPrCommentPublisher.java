package com.codereviewx.backend.review.github;

import com.codereviewx.backend.review.ReviewErrorCodes;
import org.springframework.stereotype.Component;

@Component
public class GithubPrCommentPublisher {

    public static final String AUTH_MISSING_MESSAGE =
            "Local GitHub token is not configured. Set GITHUB_TOKEN to publish GitHub PR comments.";
    public static final String AUTH_FAILED_MESSAGE =
            "GitHub authentication failed while publishing PR comment.";
    public static final String RATE_LIMITED_MESSAGE =
            "GitHub API rate limit was reached while publishing PR comment.";
    public static final String COMMENT_FAILED_MESSAGE =
            "GitHub PR comment could not be published.";

    private final GithubProperties properties;
    private final GithubPrCommentHttpClient httpClient;

    public GithubPrCommentPublisher(GithubProperties properties, GithubPrCommentHttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public GithubPrCommentPublishResult publish(GithubPrCommentPublishRequest request) {
        if (!properties.hasToken()) {
            return GithubPrCommentPublishResult.failure(AUTH_MISSING_MESSAGE);
        }

        try {
            GithubPrCommentHttpResponse response = httpClient.publishPullRequestComment(
                    properties.getApiBaseUrl(),
                    request,
                    properties.getToken(),
                    properties.getTimeoutSeconds()
            );
            return toResult(response);
        } catch (GithubPrCommentClientException ex) {
            return GithubPrCommentPublishResult.failure(COMMENT_FAILED_MESSAGE);
        }
    }

    private GithubPrCommentPublishResult toResult(GithubPrCommentHttpResponse response) {
        int status = response.statusCode();
        if (status == 201 && response.commentId() != null) {
            return GithubPrCommentPublishResult.success(response.commentId());
        }
        if (status == 401 || status == 403 && !response.rateLimited()) {
            return GithubPrCommentPublishResult.failure(AUTH_FAILED_MESSAGE);
        }
        if (status == 403 && response.rateLimited()) {
            return GithubPrCommentPublishResult.failure(RATE_LIMITED_MESSAGE);
        }
        if (status == 404) {
            return GithubPrCommentPublishResult.failure("GitHub pull request or commit target was not found.");
        }
        if (status == 422) {
            return GithubPrCommentPublishResult.failure(
                    "GitHub rejected the comment target. Check file path, line, side, and head commit."
            );
        }
        return GithubPrCommentPublishResult.failure(COMMENT_FAILED_MESSAGE);
    }
}
