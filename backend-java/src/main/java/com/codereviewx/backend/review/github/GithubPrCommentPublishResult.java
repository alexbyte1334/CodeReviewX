package com.codereviewx.backend.review.github;

public class GithubPrCommentPublishResult {

    private final boolean success;
    private final Long githubCommentId;
    private final String errorMessage;

    private GithubPrCommentPublishResult(boolean success, Long githubCommentId, String errorMessage) {
        this.success = success;
        this.githubCommentId = githubCommentId;
        this.errorMessage = errorMessage;
    }

    public static GithubPrCommentPublishResult success(Long githubCommentId) {
        return new GithubPrCommentPublishResult(true, githubCommentId, null);
    }

    public static GithubPrCommentPublishResult failure(String errorMessage) {
        return new GithubPrCommentPublishResult(false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public Long getGithubCommentId() {
        return githubCommentId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
