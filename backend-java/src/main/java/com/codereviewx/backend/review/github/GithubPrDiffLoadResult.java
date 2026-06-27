package com.codereviewx.backend.review.github;

public class GithubPrDiffLoadResult {

    private final boolean success;
    private final GithubPrDiff diff;
    private final String errorCode;
    private final String errorMessage;

    private GithubPrDiffLoadResult(boolean success, GithubPrDiff diff, String errorCode, String errorMessage) {
        this.success = success;
        this.diff = diff;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static GithubPrDiffLoadResult success(GithubPrDiff diff) {
        return new GithubPrDiffLoadResult(true, diff, null, null);
    }

    public static GithubPrDiffLoadResult failure(String errorCode, String errorMessage) {
        return new GithubPrDiffLoadResult(false, null, errorCode, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public GithubPrDiff getDiff() {
        return diff;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
